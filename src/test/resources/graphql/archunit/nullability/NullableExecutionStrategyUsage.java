package nullability;

import graphql.ExecutionResult;
import graphql.execution.AbstractAsyncExecutionStrategy;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.FieldValueInfo;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@NullMarked
public abstract class NullableExecutionStrategyUsage extends AbstractAsyncExecutionStrategy {
    public <T extends FieldValueInfo> List<T> filterSubtype(List<@Nullable T> fields) {
        return nonNullFieldValueInfos(fields);
    }

    public void nullableResults(ExecutionContext context, ExecutionStrategyParameters parameters,
                                List<String> names, List<@Nullable Object> values,
                                List<@Nullable FieldValueInfo> cancelledFields, Throwable failure) {
        CompletableFuture<ExecutionResult> result = new CompletableFuture<>();
        handleResults(context, names, result).accept(null, failure);
        handleResults(context, names, result).accept(values, null);
        completeField(context, parameters, null);
        fieldValuesCombinedBuilder(cancelledFields);
        List<FieldValueInfo> completedFields = nonNullFieldValueInfos(cancelledFields);
        CompletableFuture<@Nullable Map<String, @Nullable Object>> objectResult = new CompletableFuture<>();
        completeFieldValueMap(objectResult, context, names, values);
        handleValueException(objectResult, failure, context);
    }
}
