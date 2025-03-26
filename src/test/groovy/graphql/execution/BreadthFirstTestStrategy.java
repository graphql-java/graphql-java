package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.Internal;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * To prove we can write other execution strategies this one does a breadth first async approach
 * running all fields async first, waiting for the results
 */
@SuppressWarnings("Duplicates")
@Internal
public class BreadthFirstTestStrategy extends ExecutionStrategy {


    public BreadthFirstTestStrategy() {
        super(new SimpleDataFetcherExceptionHandler());
    }

    @Override
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {

        Map<String, FetchedValue> fetchedValues = fetchFields(executionContext, parameters);

        return completeFields(executionContext, parameters, fetchedValues);
    }

    private Map<String, FetchedValue> fetchFields(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        MergedSelectionSet fields = parameters.getFields();

        Map<String, CF<FetchedValue>> fetchFutures = new LinkedHashMap<>();

        // first fetch every value
        for (String fieldName : fields.keySet()) {
            ExecutionStrategyParameters newParameters = newParameters(parameters, fields, fieldName);

            CF fetchFuture = (CF) Async.toCompletableFuture(fetchField(executionContext, newParameters));
            fetchFutures.put(fieldName, fetchFuture);
        }

        // now wait for all fetches to finish together via this join
        allOf(fetchFutures.values()).join();

        Map<String, FetchedValue> fetchedValues = new LinkedHashMap<>();
        fetchFutures.forEach((k, v) -> fetchedValues.put(k, v.join()));
        return fetchedValues;
    }

    private CompletableFuture<ExecutionResult> completeFields(ExecutionContext executionContext, ExecutionStrategyParameters parameters, Map<String, FetchedValue> fetchedValues) {
        MergedSelectionSet fields = parameters.getFields();

        // then for every fetched value, complete it, breath first
        Map<String, Object> results = new LinkedHashMap<>();
        for (String fieldName : fetchedValues.keySet()) {
            ExecutionStrategyParameters newParameters = newParameters(parameters, fields, fieldName);

            FetchedValue fetchedValue = fetchedValues.get(fieldName);
            try {
                Object resolvedResult = completeField(executionContext, newParameters, fetchedValue).getFieldValueFuture().join();
                results.put(fieldName, resolvedResult);
            } catch (NonNullableFieldWasNullException e) {
                assertNonNullFieldPrecondition(e);
                results = null;
                break;
            }
        }
        return CF.completedFuture(new ExecutionResultImpl(results, executionContext.getErrors()));
    }

    private ExecutionStrategyParameters newParameters(ExecutionStrategyParameters parameters, MergedSelectionSet fields, String fieldName) {
        MergedField currentField = fields.getSubField(fieldName);
        ResultPath fieldPath = parameters.getPath().segment(fieldName);
        return parameters
                .transform(builder -> builder.field(currentField).path(fieldPath));
    }


    public static <T> CF<List<T>> allOf(final Collection<CF<T>> futures) {
        CF[] cfs = futures.toArray(new CF[futures.size()]);

        return CF.allOf(cfs)
                .thenApply(vd -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList())
                );
    }
}
