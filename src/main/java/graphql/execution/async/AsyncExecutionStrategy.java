package graphql.execution.async;

import com.spotify.futures.CompletableFutures;
import graphql.ExceptionWhileDataFetching;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLException;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategy;
import graphql.language.Field;
import graphql.schema.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Objects.isNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;


public final class AsyncExecutionStrategy extends ExecutionStrategy {

    public static AsyncExecutionStrategy serial() {
        return new AsyncExecutionStrategy(true, null);
    }

    public static AsyncExecutionStrategy serial(final CompletableFutureFactory factory) {
        return new AsyncExecutionStrategy(true, factory);
    }

    public static AsyncExecutionStrategy parallel() {
        return new AsyncExecutionStrategy(false, null);
    }

    public static AsyncExecutionStrategy parallel(final CompletableFutureFactory factory) {
        return new AsyncExecutionStrategy(false, factory);
    }

    private static final Logger log = LoggerFactory.getLogger(AsyncExecutionStrategy.class);

    private final boolean serial;
    private final CompletableFutureFactory completableFutureFactory;

    private AsyncExecutionStrategy(boolean serial, final CompletableFutureFactory completableFutureFactory) {
        this.serial = serial;
        if (isNull(completableFutureFactory)) {
            this.completableFutureFactory = DefaultCompletableFutureFactory.defaultFactory();
        } else {
            this.completableFutureFactory = completableFutureFactory;
        }
    }

    @Override
    public ExecutionResult execute(ExecutionContext executionContext, GraphQLObjectType parentType, Object source, Map<String, List<Field>> fields) {
        try {
            return executeAsync(executionContext, parentType, source, fields).toCompletableFuture().get();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public CompletionStage<ExecutionResult> executeAsync(ExecutionContext executionContext, GraphQLObjectType parentType, Object source, Map<String, List<Field>> fields) {

        Map<String, Supplier<CompletionStage<ExecutionResult>>> fieldsToExecute = fields.keySet()
          .stream()
          .collect(Collectors.toMap(
            Function.identity(),
            field -> () -> resolveFieldAsync(executionContext, parentType, source, fields.get(field)),
            (a, b) -> a,
            LinkedHashMap::new
          ));

        return executeFields(fieldsToExecute).thenApply(resultMap -> {
            Map<String, Object> dataMap = new HashMap<>();
            resultMap.forEach((key, result) -> {
                dataMap.put(key, result.getData());
            });
            return new ExecutionResultImpl(dataMap, executionContext.getErrors());
        });
    }

    protected CompletionStage<ExecutionResult> resolveFieldAsync(ExecutionContext executionContext, GraphQLObjectType parentType, Object source, List<Field> fields) {
        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext.getGraphQLSchema(), parentType, fields.get(0));

        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(fieldDef.getArguments(), fields.get(0).getArguments(), executionContext.getVariables());
        DataFetchingEnvironment environment = new DataFetchingEnvironment(
          source,
          argumentValues,
          executionContext.getRoot(),
          fields,
          fieldDef.getType(),
          parentType,
          executionContext.getGraphQLSchema()
        );

        CompletionStage<?> stage;
        try {
            Object resolvedValue = fieldDef.getDataFetcher().get(environment);
            stage = resolvedValue instanceof CompletionStage ? (CompletionStage<?>) resolvedValue : completedFuture(resolvedValue);
        } catch (Exception e) {
            log.warn("Exception while fetching data", e);
            executionContext.addError(new ExceptionWhileDataFetching(e));
            stage = completedFuture(null);
        }

        return stage.exceptionally(e -> {
            log.warn("Exception while fetching data", e);
            executionContext.addError(new ExceptionWhileDataFetching(e));
            return null;
        }).thenCompose(o -> completeValueAsync(executionContext, fieldDef.getType(), fields, o));
    }

    protected CompletionStage<ExecutionResult> completeValueAsync(ExecutionContext executionContext, GraphQLType fieldType, List<Field> fields, Object result) {
        if (fieldType instanceof GraphQLNonNull) {
            return completeValueAsync(executionContext, ((GraphQLNonNull) fieldType).getWrappedType(), fields, result).thenApply(result1 -> {
                if (isNull(result1.getData())) {
                    throw new GraphQLException("Cannot return null for non-nullable type: " + fields);
                }
                return result1;
            });
        } else if (isNull(result)) {
            return completedFuture(new ExecutionResultImpl(null, null));
        } else if (fieldType instanceof GraphQLList) {
            if (result.getClass().isArray()) {
                result = asList((Object[]) result);
            }
            return completeValueForListAsync(executionContext, (GraphQLList) fieldType, fields, (Iterable<Object>) result);
        } else if (fieldType instanceof GraphQLScalarType) {
            return completedFuture(completeValueForScalar((GraphQLScalarType) fieldType, result));
        } else if (fieldType instanceof GraphQLEnumType) {
            return completedFuture(completeValueForEnum((GraphQLEnumType) fieldType, result));
        }

        GraphQLObjectType resolvedType;
        if (fieldType instanceof GraphQLInterfaceType) {
            resolvedType = resolveType((GraphQLInterfaceType) fieldType, result);
        } else if (fieldType instanceof GraphQLUnionType) {
            resolvedType = resolveType((GraphQLUnionType) fieldType, result);
        } else {
            resolvedType = (GraphQLObjectType) fieldType;
        }

        Map<String, List<Field>> subFields = new LinkedHashMap<>();
        List<String> visitedFragments = new ArrayList<>();
        for (Field field : fields) {
            if (isNull(field.getSelectionSet())) continue;
            fieldCollector.collectFields(executionContext, resolvedType, field.getSelectionSet(), visitedFragments, subFields);
        }

        // Calling this from the executionContext to ensure we shift back from mutation strategy to the query strategy.
        AsyncExecutionStrategy queryStrategy = (AsyncExecutionStrategy) executionContext.getQueryStrategy();
        return queryStrategy.executeAsync(executionContext, resolvedType, result, subFields);
    }

    protected CompletionStage<ExecutionResult> completeValueForListAsync(ExecutionContext executionContext, GraphQLList fieldType, List<Field> fields, Iterable<Object> result) {
        List<CompletionStage<ExecutionResult>> completedResults = new ArrayList<>();
        for (Object item : result) {
            CompletionStage<ExecutionResult> completedValue = completeValueAsync(executionContext, fieldType.getWrappedType(), fields, item);
            completedResults.add(completedValue);
        }
        return CompletableFutures.allAsList(completedResults).thenApply(results -> {
            List<Object> items = results.stream().map(ExecutionResult::getData).collect(toList());
            return new ExecutionResultImpl(items, null);
        });
    }

    private <K, V> CompletionStage<Map<K, V>> executeFields(Map<K, Supplier<CompletionStage<V>>> map) {
       if (serial) {
            List<Map.Entry<K, Supplier<CompletionStage<V>>>> resolvers = new ArrayList<>(map.entrySet());
            LinkedHashMap<K, V> results = new LinkedHashMap<>();
            return executeInSerial(resolvers, results, 0);
        } else {
            return executeInParallel(map);
        }
    }

    private <K, V> CompletionStage<Map<K, V>> executeInSerial(List<Map.Entry<K, Supplier<CompletionStage<V>>>> resolvers, Map<K, V> results, int i) {
        return resolvers.get(i).getValue().get().thenCompose(result -> {
            results.put(resolvers.get(i).getKey(), result);
            if (i == resolvers.size() - 1) {
                return completedFuture(results);
            } else {
                return executeInSerial(resolvers, results, i + 1);
            }
        });
    }

    /**
     * `ConcurrentHashMap`, used by `executeInParallel()`, does not allow null keys or values
     */
    private static final Object NULL = new Object();

    private <K, V> CompletionStage<Map<K, V>> executeInParallel(Map<K, Supplier<CompletionStage<V>>> resolvers) {
        CompletableFuture<Map<K, V>> future = completableFutureFactory.future();
        Set<K> awaiting = new ConcurrentHashMap<>(new HashMap<>(resolvers)).keySet();  // `keySet()` is a view and will be modified, so copy first
        Map<K, V> results = new ConcurrentHashMap<>();
        resolvers.entrySet().forEach(entry -> {
            entry.getValue().get().thenAccept(result -> {
                K key = entry.getKey();
                results.put(key, result);
                awaiting.remove(key);
                if (awaiting.isEmpty()) {
                    Map<K, V> map = new LinkedHashMap<>();
                    resolvers.keySet().forEach(key1 -> {
                        V value = results.get(key1);
                        map.put(key1, value == NULL ? null : value);
                    });
                    future.complete(map);
                }
            });
        });
        return future;
    }
}
