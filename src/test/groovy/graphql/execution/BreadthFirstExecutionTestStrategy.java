package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.Internal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * To prove we can write other execution strategies this one does a breath first approach
 */
@Internal
public class BreadthFirstExecutionTestStrategy extends ExecutionStrategy {

    public BreadthFirstExecutionTestStrategy() {
        super(new SimpleDataFetcherExceptionHandler());
    }

    @Override
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
        MergedSelectionSet fields = parameters.getFields();

        Map<String, FetchedValue> fetchedValues = new LinkedHashMap<>();

        // first fetch every value
        for (String fieldName : fields.keySet()) {
            FetchedValue fetchedValue = fetchField(executionContext, parameters, fields, fieldName);
            fetchedValues.put(fieldName, fetchedValue);
        }

        // then for every fetched value, complete it
        Map<String, Object> results = new LinkedHashMap<>();
        for (String fieldName : fetchedValues.keySet()) {
            MergedField currentField = fields.getSubField(fieldName);
            FetchedValue fetchedValue = fetchedValues.get(fieldName);

            ExecutionPath fieldPath = parameters.getPath().segment(fieldName);
            ExecutionStrategyParameters newParameters = parameters
                    .transform(builder -> builder.field(currentField).path(fieldPath));

            try {
                completeValue(executionContext, results, fieldName, fetchedValue, newParameters);
            } catch (NonNullableFieldWasNullException e) {
                assertNonNullFieldPrecondition(e);
                results = null;
                break;
            }
        }
        return CompletableFuture.completedFuture(new ExecutionResultImpl(results, executionContext.getErrors()));
    }

    private FetchedValue fetchField(ExecutionContext executionContext, ExecutionStrategyParameters parameters, MergedSelectionSet fields, String fieldName) {
        MergedField currentField = fields.getSubField(fieldName);

        ExecutionPath fieldPath = parameters.getPath().segment(fieldName);
        ExecutionStrategyParameters newParameters = parameters
                .transform(builder -> builder.field(currentField).path(fieldPath));

        return fetchField(executionContext, newParameters).join();
    }

    private void completeValue(ExecutionContext executionContext, Map<String, Object> results, String fieldName, FetchedValue fetchedValue, ExecutionStrategyParameters newParameters) {
        ExecutionResult resolvedResult = completeField(executionContext, newParameters, fetchedValue).getFieldValue().join();
        results.put(fieldName, resolvedResult != null ? resolvedResult.getData() : null);
    }

}
