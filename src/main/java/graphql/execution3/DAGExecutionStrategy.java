/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.execution.Async;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionPath;
import graphql.execution.ExecutionStepInfo;
import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo;
import graphql.execution.ExecutionStepInfoFactory;
import graphql.execution2.FetchedValue;
import graphql.execution2.ValueFetcher;
import graphql.language.Field;
import graphql.language.Node;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLType;
import graphql.util.DependenciesIterator;
import graphql.util.Edge;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author gkesler
 */
public class DAGExecutionStrategy implements ExecutionStrategy {
    public DAGExecutionStrategy (ExecutionContext executionContext) {
        this.executionContext = Objects.requireNonNull(executionContext);
        this.executionInfoFactory = new ExecutionStepInfoFactory();
        this.valueFetcher = new ValueFetcher(executionContext);
        this.resultCollector = new ResultCollector();
    }
    
    @Override
    public CompletableFuture<ExecutionResult> execute(ExecutionPlan executionPlan) {
        Objects.requireNonNull(executionPlan);
        
        ExecutionPlanContextImpl executionPlanContext = new ExecutionPlanContextImpl();
        return CompletableFuture
                .completedFuture(executionPlan.orderDependencies(executionPlanContext))
                .thenCompose(this::resolveClosure)
                .thenApply(noResult -> executionPlanContext.getResult());
    }
    
    private CompletableFuture<DependenciesIterator<NodeVertex<Node, GraphQLType>>> resolveClosure (DependenciesIterator<NodeVertex<Node, GraphQLType>> closure) {
        if (closure.hasNext()) {
            Collection<NodeVertex<Node, GraphQLType>> nodes = closure.next();
            List<CompletableFuture<Void>> tasks = new ArrayList<>(nodes.size());

            Iterator<NodeVertex<Node, GraphQLType>> toBeResolved = nodes.iterator();
            while (toBeResolved.hasNext()) {
                NodeVertex<Node, GraphQLType> field = toBeResolved.next();
                toBeResolved.remove();

                tasks.add(
                    CompletableFuture
                        .completedFuture(field)
                        .thenCompose(this::fetchNode)
                        .thenAccept(closure::close)
                );
            }

            // execute fetch&resove asynchronously
            return Async
                .each(tasks)
                .thenApply(noResult -> closure)
                .thenCompose(this::resolveClosure);
        } else {
            return CompletableFuture
                .completedFuture(closure);
        }               
    }
    
    private void provideSource (NodeVertex<Node, GraphQLType> source, FieldVertex sink) {
        LOGGER.info("provideSource: source={}, sink={}", source, sink);
        source.accept(sink, new NodeVertexVisitor<FieldVertex>() {
            @Override
            public FieldVertex visit(OperationVertex source, FieldVertex sink) {
                resultCollector.prepareResult(source);
                source
                    .executionStepInfo(
                        newExecutionStepInfo()
                            .type((GraphQLOutputType)source.getType())
                            .path(ExecutionPath.rootPath())
                            .build()
                    );

                return visitNode(source, sink.root(true));
            }

            @Override
            public FieldVertex visitNode(NodeVertex<? extends Node, ? extends GraphQLType> source, FieldVertex sink) {
                Object result = source.getResult();
                return sink
                    .parentExecutionStepInfo(source.getExecutionStepInfo())
                    .source(ResultCollector.flatten((List<Object>)source.getResult()));
            }
        });
    }
    
    private void joinResults (FieldVertex source, NodeVertex<Node, GraphQLType> sink) {
        LOGGER.info("afterResolve: source={}, sink={}", source, sink);
        resultCollector.joinResultsOf(source);
    }

    private boolean tryResolve (NodeVertex<Node, GraphQLType> node) {
        LOGGER.info("tryResolve: node={}", node);
        return node.accept(true, new NodeVertexVisitor<Boolean>() {
            @Override
            public Boolean visit(FieldVertex node, Boolean data) {
                return false;
            }

            @Override
            public Boolean visit(DocumentVertex node, Boolean data) {
                resultCollector.prepareResult(node);
                return NodeVertexVisitor.super.visit(node, data);
            }            
        });
    }

    private CompletableFuture<NodeVertex<Node, GraphQLType>> fetchNode (NodeVertex<Node, GraphQLType> node) {  
        LOGGER.info("fetchNode: node={}", node);
        
        FieldVertex fieldNode = node.as(FieldVertex.class);
        List<Field> sameFields = Collections.singletonList(fieldNode.getNode());
        ExecutionStepInfo executionStepInfo = executionInfoFactory
            .newExecutionStepInfoForSubField(executionContext, sameFields, node.getParentExecutionStepInfo());
        
        Supplier<CompletableFuture<List<FetchedValue>>> valuesSupplier = fieldNode.isRoot()
            ? () -> fetchRootValues(executionContext.getRoot(), sameFields, executionStepInfo)
            : () -> fetchBatchedValues((List<Object>)fieldNode.getSource(), sameFields, executionStepInfo);


        return valuesSupplier.get()
                .thenApply(fetchedValues -> 
                    fetchedValues
                        .stream()
                        .map(FetchedValue::getFetchedValue)
                        .map(fv -> resultCollector.checkAndFixNILs(fv, fieldNode))
                        .collect(Collectors.toList())
                )
                .thenApply(fetchedValues -> 
                    (NodeVertex<Node, GraphQLType>)node
                        .executionStepInfo(executionStepInfo)
                        .result(fetchedValues)
                );
    }
    
    private CompletableFuture<List<FetchedValue>> fetchRootValues (Object root, List<Field> sameFields, ExecutionStepInfo executionStepInfo) {
        return valueFetcher
            .fetchValue(root, sameFields, executionStepInfo)
            .thenApply(Collections::singletonList);
    }
    
    private CompletableFuture<List<FetchedValue>> fetchBatchedValues (List<Object> sources, List<Field> sameFields, ExecutionStepInfo executionStepInfo) {
        return valueFetcher
            .fetchBatchedValues(sources, sameFields, 
                Stream
                    .generate(() -> executionStepInfo)
                    .limit(sources.size())
                    .collect(Collectors.toList())
            );
    }

    private class ExecutionPlanContextImpl implements ExecutionPlanContext {
        @Override
        public void prepareResolve(Edge<? extends NodeVertex<? extends Node, ? extends GraphQLType>, ?> edge) {
            provideSource((NodeVertex<Node, GraphQLType>)edge.getSource(), (FieldVertex)edge.getSink());
        }

        @Override
        public void whenResolved(Edge<? extends NodeVertex<? extends Node, ? extends GraphQLType>, ?> edge) {
            joinResults((FieldVertex)edge.getSource(), (NodeVertex<Node, GraphQLType>)edge.getSink());
        }

        @Override
        public boolean resolve(NodeVertex<? extends Node, ? extends GraphQLType> node) {
            return tryResolve((NodeVertex<Node, GraphQLType>)node);
        }

        public ExecutionResult getResult() {
            return new ExecutionResultImpl(resultCollector.getResult(), executionContext.getErrors());
        }
    };
    
    private final ExecutionContext executionContext;
    private final ExecutionStepInfoFactory executionInfoFactory;
    private final ValueFetcher valueFetcher;
    private final ResultCollector resultCollector;
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DAGExecutionStrategy.class);
}
