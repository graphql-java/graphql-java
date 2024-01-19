package graphql.execution.defer;

import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.execution.Async;
import graphql.execution.ResultPath;
import graphql.incremental.DeferPayload;
import graphql.incremental.DelayedIncrementalExecutionResult;
import graphql.incremental.DelayedIncrementalExecutionResultImpl;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This represents a deferred call (aka @defer) to get an execution result sometime after
 * the initial query has returned
 */
@Internal
public class DeferredCall {
    private final String label;
    private final ResultPath path;
    private final List<Supplier<CompletableFuture<FieldWithExecutionResult>>> calls;
    private final DeferredErrorSupport errorSupport;

    public DeferredCall(
            String label,
            ResultPath path,
            List<Supplier<CompletableFuture<FieldWithExecutionResult>>> calls,
            DeferredErrorSupport deferredErrorSupport
    ) {
        this.label = label;
        this.path = path;
        this.calls = calls;
        this.errorSupport = deferredErrorSupport;
    }

    CompletableFuture<DeferPayload> invoke() {
        Async.CombinedBuilder<FieldWithExecutionResult> futures = Async.ofExpectedSize(calls.size());

        calls.forEach(call -> futures.add(call.get()));

        return futures.await()
                .thenApply(this::transformToDeferredPayload);
    }

    private DeferPayload transformToDeferredPayload(List<FieldWithExecutionResult> fieldWithExecutionResults) {
        // TODO: Not sure how/if this errorSupport works
        List<GraphQLError> errorsEncountered = errorSupport.getErrors();

        Map<String, Object> data = fieldWithExecutionResults.stream()
                .collect(Collectors.toMap(
                        fieldWithExecutionResult -> fieldWithExecutionResult.fieldName,
                        fieldWithExecutionResult -> fieldWithExecutionResult.executionResult.getData()
                ));

        return DeferPayload.newDeferredItem()
                .errors(errorsEncountered)
                .path(path)
                .label(label)
                .data(data)
                .build();
    }

    public static class FieldWithExecutionResult {
        private final String fieldName;
        private final ExecutionResult executionResult;

        public FieldWithExecutionResult(String fieldName, ExecutionResult executionResult) {
            this.fieldName = fieldName;
            this.executionResult = executionResult;
        }
    }
}
