/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionPath;
import graphql.execution.ExecutionStepInfo;
import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo;
import graphql.execution.ExecutionStepInfoFactory;
import graphql.execution2.FetchedValueAnalysis;
import graphql.execution2.FetchedValueAnalyzer;
import graphql.execution2.ResultNodesCreator;
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
        this.fetchedValueAnalyzer = new FetchedValueAnalyzer(executionContext);
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
            return CompletableFuture
                    .allOf(tasks.toArray(EMPTY_STAGES))
                    .thenApply(noResult -> closure)
                    .thenCompose(this::resolveClosure);
        } else {
            return CompletableFuture
                    .completedFuture(closure);
        }               
    }
    
    private void provideSource (NodeVertex<Node, GraphQLType> source, FieldVertex sink) {
        LOGGER.info("provideSource: source={}, sink={}", source, sink);
        source.accept(null, new NodeVertexVisitor<Object>() {
            @Override
            public Object visit(FieldVertex node, Object data) {
                // sub-field
                return sink
                    .parentExecutionStepInfo(source.getExecutionStepInfo())
                    .source(node.getResult());
            }

            @Override
            public Object visitNode(NodeVertex<? extends Node, ? extends GraphQLType> node, Object data) {
                // root field
                return sink
                    .parentExecutionStepInfo(newExecutionStepInfo()
                            .type((GraphQLOutputType)node.getType())
                            .path(ExecutionPath.rootPath())
                            .build()
                    )
                    .source(executionContext.getRoot());
            }
        });
    }

    private void afterResolve (NodeVertex<Node, GraphQLType> source, NodeVertex<Node, GraphQLType> sink) {
        LOGGER.info("afterResolve: source={}, sink={}", source, sink);
    }

    private boolean resolveNode (NodeVertex<Node, GraphQLType> node) {
        LOGGER.info("resolveNode: {}", node);
        return node.accept(true, new NodeVertexVisitor<Boolean>() {
            @Override
            public Boolean visit(FieldVertex node, Boolean data) {
                return false;
            }
        });
    }

    private CompletableFuture<NodeVertex<Node, GraphQLType>> fetchNode (NodeVertex<Node, GraphQLType> node) {  
        LOGGER.info("fetchNode: {}", node);
        
        FieldVertex fieldNode = (FieldVertex)(NodeVertex<? extends Node, ? extends GraphQLType>)node;
        List<Field> sameFields = Collections.singletonList(fieldNode.getNode());
        ExecutionStepInfo executionStepInfo = executionInfoFactory.newExecutionStepInfoForSubField(executionContext, sameFields, node.getParentExecutionStepInfo());
        node.executionStepInfo(executionStepInfo);
        
        return valueFetcher
                .fetchValue(node.getSource(), sameFields, executionStepInfo)
                .thenApply(fetchedValue -> {
                    FetchedValueAnalysis fetchedValueAnalysis = fetchedValueAnalyzer.analyzeFetchedValue(fetchedValue.getFetchedValue(), fieldNode.getResponseKey(), sameFields, executionStepInfo);
                    fetchedValueAnalysis.setFetchedValue(fetchedValue);
                    return fetchedValueAnalysis;
                })
                .thenApply(o -> (NodeVertex<Node, GraphQLType>)node.result(o));
    }
    
    private class ExecutionPlanContextImpl implements ExecutionPlanContext {
        @Override
        public void prepareResolve(Edge<? extends NodeVertex<? extends Node, ? extends GraphQLType>, ?> edge) {
            provideSource((NodeVertex<Node, GraphQLType>)edge.getSource(), (FieldVertex)edge.getSink());
        }

        @Override
        public void whenResolved(Edge<? extends NodeVertex<? extends Node, ? extends GraphQLType>, ?> edge) {
            afterResolve((NodeVertex<Node, GraphQLType>)edge.getSource(), (NodeVertex<Node, GraphQLType>)edge.getSink());
        }

        @Override
        public boolean resolve(NodeVertex<? extends Node, ? extends GraphQLType> node) {
            return resolveNode((NodeVertex<Node, GraphQLType>)node);
        }

        public ExecutionResult getResult() {
            return result;
        }
        
        // FIXME
        ExecutionResult result = new ExecutionResultImpl(Collections.emptyList());
    };
    
    private final ExecutionContext executionContext;
    private final ExecutionStepInfoFactory executionInfoFactory;
    private final ValueFetcher valueFetcher;
    private final FetchedValueAnalyzer fetchedValueAnalyzer;
    private final ResultNodesCreator resultNodesCreator = new ResultNodesCreator();
    
    private static final CompletableFuture<?>[] EMPTY_STAGES = {};
    private static final Logger LOGGER = LoggerFactory.getLogger(DAGExecutionStrategy.class);
}
