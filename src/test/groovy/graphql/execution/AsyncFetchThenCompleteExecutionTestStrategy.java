package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.Internal;
import graphql.language.Field;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.supplyAsync;

/**
 * To prove we can write other execution strategies this one does a breath first asynch approach
 * running all fields asynch first, waiting for the results
 */
@SuppressWarnings("Duplicates")
@Internal
public class AsyncFetchThenCompleteExecutionTestStrategy extends ExecutionStrategy {

    private final ExecutorService executor;

    public AsyncFetchThenCompleteExecutionTestStrategy() {
        super(new SimpleDataFetcherExceptionHandler());
        this.executor = ForkJoinPool.commonPool();
    }

    @Override
    public ExecutionResult execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {

        Map<String, Object> fetchedValues = fetchFields(executionContext, parameters);

        return completeFields(executionContext, parameters, fetchedValues);
    }

    private Map<String, Object> fetchFields(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        Map<String, List<Field>> fields = parameters.fields();

        Map<String, CompletableFuture<Object>> fetchFutures = new LinkedHashMap<>();

        // first fetch every value
        for (String fieldName : fields.keySet()) {
            List<Field> fieldList = fields.get(fieldName);

            ExecutionStrategyParameters newParameters = newParameters(parameters, fieldName);

            CompletableFuture<Object> fetchFuture = supplyAsync(() ->
                    fetchField(executionContext, newParameters, fieldList), executor);
            fetchFutures.put(fieldName, fetchFuture);
        }

        // now wait for all fetches to finish together via this join
        allOf(fetchFutures.values()).join();

        Map<String, Object> fetchedValues = new LinkedHashMap<>();
        fetchFutures.forEach((k, v) -> fetchedValues.put(k, v.join()));
        return fetchedValues;
    }

    private ExecutionResult completeFields(ExecutionContext executionContext, ExecutionStrategyParameters parameters, Map<String, Object> fetchedValues) {
        Map<String, List<Field>> fields = parameters.fields();

        // then for every fetched value, complete it, breath first
        Map<String, Object> results = new LinkedHashMap<>();
        for (String fieldName : fetchedValues.keySet()) {
            List<Field> fieldList = fields.get(fieldName);

            ExecutionStrategyParameters newParameters = newParameters(parameters, fieldName);

            Object fetchedValue = fetchedValues.get(fieldName);
            try {
                ExecutionResult resolvedResult = completeField(executionContext, newParameters, fieldList, fetchedValue);
                results.put(fieldName, resolvedResult != null ? resolvedResult.getData() : null);
            } catch (NonNullableFieldWasNullException e) {
                assertNonNullFieldPrecondition(e);
                results = null;
                break;
            }
        }
        return new ExecutionResultImpl(results, executionContext.getErrors());
    }

    private ExecutionStrategyParameters newParameters(ExecutionStrategyParameters parameters, String fieldName) {
        ExecutionPath fieldPath = parameters.path().segment(fieldName);
        return parameters.transform(builder -> builder.path(fieldPath));
    }


    public static <T> CompletableFuture<List<T>> allOf(final Collection<CompletableFuture<T>> futures) {
        CompletableFuture[] cfs = futures.toArray(new CompletableFuture[futures.size()]);

        return CompletableFuture.allOf(cfs)
                .thenApply(vd -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList())
                );
    }
}
