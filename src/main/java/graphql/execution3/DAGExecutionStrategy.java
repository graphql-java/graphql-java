/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import graphql.Assert;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
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
//        this.fetchedValueAnalyzer = new FetchedValueAnalyzer(executionContext);
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
        source.accept(sink, sourceProvider);
    }
    
    private final NodeVertexVisitor<FieldVertex> sourceProvider = new NodeVertexVisitor<FieldVertex>() {
        @Override
        public FieldVertex visit(OperationVertex source, FieldVertex sink) {
            resultCollector.operation(source
                .executionStepInfo(
                    newExecutionStepInfo()
                        .type((GraphQLOutputType)source.getType())
                        .path(ExecutionPath.rootPath())
                        .build()
                )
                .result(Collections.singletonList(new HashMap<>())));
            
            return visitNode(source, sink.root(true));
        }
        
        @Override
        public FieldVertex visitNode(NodeVertex<? extends Node, ? extends GraphQLType> source, FieldVertex sink) {
            Object result = source.getResult();
            return sink
                .parentExecutionStepInfo(source.getExecutionStepInfo())
                .source(flatten((List<Object>)source.getResult()));
        }
    };
    
    private void joinResults (FieldVertex source, NodeVertex<Node, GraphQLType> sink) {
        LOGGER.info("afterResolve: source={}, sink={}", source, sink);
//        resultCollector.joinOn(source.getResponseKey(), (List<Object>)source.getResult(), (List<Object>)source.getSource());
        resultCollector.joinResultsOf(source);
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
//        Object source = fieldNode.isRoot() ? executionContext.getRoot() : asObject(fieldNode.getSource());
//        List<Object> sources = fieldNode.isRoot() ? asList(executionContext.getRoot()) : (List<Object>)fieldNode.getSource();
        List<Field> sameFields = Collections.singletonList(fieldNode.getNode());
        ExecutionStepInfo executionStepInfo = executionInfoFactory.newExecutionStepInfoForSubField(executionContext, sameFields, node.getParentExecutionStepInfo());
//        List<ExecutionStepInfo> executionStepInfos = Stream
//                .generate(() -> executionStepInfo)
//                .limit(sources.size())
//                .collect(Collectors.toList());
        Supplier<CompletableFuture<List<FetchedValue>>> valuesSupplier = fieldNode.isRoot()
            ? () -> fetchRootValues(executionContext.getRoot(), sameFields, executionStepInfo)
            : () -> fetchBatchedValues((List<Object>)fieldNode.getSource(), sameFields, executionStepInfo);


        // FIXME: in batch mode source object *always* must be a list
        // extract the element for now if the list is a singleton
        
//        return valueFetcher
//                .fetchValue(source, sameFields, executionStepInfo)
//                .thenApply(fetchedValue -> asList(fetchedValue.getFetchedValue()))
//                .thenApply(fetchedValue -> (NodeVertex<Node, GraphQLType>)node
//                        .executionStepInfo(executionStepInfo)
//                        .result(fetchedValue));
        return valuesSupplier.get()
                .thenApply(fetchedValues -> fetchedValues
                        .stream()
                        .map(FetchedValue::getFetchedValue)
                        .map(fv -> checkAndFixNILs(fv, fieldNode))
                        .collect(Collectors.toList())
                )
                .thenApply(fetchedValues -> (NodeVertex<Node, GraphQLType>)node
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
    
    private Object checkAndFixNILs (Object fetchedValue, FieldVertex fieldNode) {
        return (isNIL(fetchedValue) || fieldNode.getCardinality() == FieldVertex.Cardinality.OneToOne)
            ? fixNIL(fetchedValue, fieldNode)
            : fixNILs((List<Object>)fetchedValue, fieldNode);
    }
        
    private static boolean isNIL (Object value) {
        return value == null || value == ValueFetcher.NULL_VALUE;
    }
    
    private static Object fixNIL (Object fetchedValue, FieldVertex fieldNode) {
        if (isNIL(fetchedValue)) {
            if (fieldNode.isNotNull()) {
                // FIXME: report error?
            }
            
            return null;
        }
        
        return fetchedValue;
    }

    private static Object fixNILs (List<Object> fetchedValues, FieldVertex fieldNode) {
        boolean hasNulls[] = {false};
        fetchedValues = flatten(fetchedValues, o -> true)
            .stream()
            .map(fetchedValue -> {
                if (isNIL(fetchedValue)) {
                    hasNulls[0] = true;
                    return null;
                } else {
                    return fetchedValue;
                }
            })
            .collect(Collectors.toList());
        
        if (hasNulls[0]) {
            if (fieldNode.isNotNullItems()) {
                return null;
            }
        }
        
        return fetchedValues;
    }

    public static List<Object> flatten (List<Object> result) {
        return flatten(result, o -> o != null);
    }
    
    public static List<Object> flatten (List<Object> result, Predicate<? super Object> filter) {
        Objects.requireNonNull(filter);
        
        return Optional
            .ofNullable(result)
            .map(res -> res
                .stream()
                .flatMap(DAGExecutionStrategy::asStream)
                .filter(filter)
                .collect(Collectors.toList())
            )
            .orElseGet(Collections::emptyList);
    }

    private static Stream<Object> asStream (Object o) {
        return (o instanceof Collection)
            ? ((Collection<Object>)o)
                .stream()
                .flatMap(DAGExecutionStrategy::asStream)
            : Stream.of(o);
    }

    private static List<Object> asList (Object o) {
        return (o instanceof List)
            ? (List<Object>)o
            : Collections.singletonList(o);
    }
    
    private static Object asObject (Object o) {
        List<Object> singletonList;
        return (o instanceof List && (singletonList = (List<Object>)o).size() <= 1)
            ? singletonList.size() == 1 
                ? singletonList.get(0) 
                : null
            : o;
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
            return resolveNode((NodeVertex<Node, GraphQLType>)node);
        }

        public ExecutionResult getResult() {
            return new ExecutionResultImpl(resultCollector.getResult(), executionContext.getErrors());
        }
    };
    
    private final ExecutionContext executionContext;
    private final ExecutionStepInfoFactory executionInfoFactory;
    private final ValueFetcher valueFetcher;
//    private final FetchedValueAnalyzer fetchedValueAnalyzer;
    private final ResultCollector resultCollector = new ResultCollector();
    
    private static final CompletableFuture<?>[] EMPTY_STAGES = {};
    private static final Logger LOGGER = LoggerFactory.getLogger(DAGExecutionStrategy.class);
}
