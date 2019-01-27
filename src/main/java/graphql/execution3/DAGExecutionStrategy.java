/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import static graphql.Assert.assertNotNull;
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
import graphql.util.TriFunction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
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
        assertNotNull(executionPlan);
        
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
        LOGGER.debug("provideSource: source={}, sink={}", source, sink);
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

                return visitNode(source, sink);
            }

            @Override
            public FieldVertex visitNode(NodeVertex<? extends Node, ? extends GraphQLType> source, FieldVertex sink) {
                return sink
                    .parentExecutionStepInfo(source.getExecutionStepInfo())
                    .source(ResultCollector.flatten((List<Object>)source.getResult()));
            }
        });
    }
    
    private void joinResults (FieldVertex source, NodeVertex<Node, GraphQLType> sink) {
        LOGGER.debug("afterResolve: source={}, sink={}", source, sink);
        resultCollector.joinResultsOf(source);
    }

    private boolean tryResolve (NodeVertex<Node, GraphQLType> node) {
        LOGGER.debug("tryResolve: node={}", node);
        return node.accept(true, new NodeVertexVisitor<Boolean>() {
            @Override
            public Boolean visit(FieldVertex node, Boolean data) {
                List<Object> sources = (List<Object>)node.getSource();
                // if sources is empty, no need to fetch data.
                // even if it is fetched, it won't be joined anyways
                return sources.isEmpty();
            }

            @Override
            public Boolean visit(DocumentVertex node, Boolean data) {
                resultCollector.prepareResult(node);
                return true;
            }            
        });
    }

    private CompletableFuture<NodeVertex<Node, GraphQLType>> fetchNode (NodeVertex<Node, GraphQLType> node) {  
        LOGGER.debug("fetchNode: node={}", node);
        
        FieldVertex fieldNode = node.as(FieldVertex.class);
        List<Field> sameFields = Collections.singletonList(fieldNode.getNode());
        ExecutionStepInfo executionStepInfo = executionInfoFactory
            .newExecutionStepInfoForSubField(executionContext, sameFields, node.getParentExecutionStepInfo());
        
        TriFunction<FieldVertex, List<Field>, ExecutionStepInfo, CompletableFuture<List<FetchedValue>>> valuesFetcher = 
            fieldNode.isRoot() ? this::fetchRootValues : this::fetchBatchedValues;


        return valuesFetcher.apply(fieldNode, sameFields, executionStepInfo)
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
    
    private CompletableFuture<List<FetchedValue>> fetchRootValues (FieldVertex fieldNode, List<Field> sameFields, ExecutionStepInfo executionStepInfo) {
        return valueFetcher
            .fetchValue(executionContext.getRoot(), sameFields, executionStepInfo)
            .thenApply(Collections::singletonList);
    }
    
    private CompletableFuture<List<FetchedValue>> fetchBatchedValues (FieldVertex fieldNode, List<Field> sameFields, ExecutionStepInfo executionStepInfo) {
        List<Object> sources = (List<Object>)fieldNode.getSource();
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
