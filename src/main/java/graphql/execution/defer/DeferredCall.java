package graphql.execution.defer;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.Internal;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * This represents a deferred call (aka @defer) to get an execution result sometime after
 * the initial query has returned
 */
@Internal
public class DeferredCall {
    private final Supplier<CompletableFuture<ExecutionResult>> call;
    private final DeferredErrorSupport errorSupport;

    public DeferredCall(Supplier<CompletableFuture<ExecutionResult>> call, DeferredErrorSupport deferredErrorSupport) {
        this.call = call;
        this.errorSupport = deferredErrorSupport;
    }

    CompletableFuture<ExecutionResult> invoke() {
        CompletableFuture<ExecutionResult> future = call.get();
        return future.thenApply(this::addErrorsEncountered);
    }

    private ExecutionResult addErrorsEncountered(ExecutionResult executionResult) {
        List<GraphQLError> errorsEncountered = errorSupport.getErrors();
        if (errorsEncountered.isEmpty()) {
            return executionResult;
        }
        ExecutionResultImpl sourceResult = (ExecutionResultImpl) executionResult;
        ExecutionResultImpl.Builder builder = ExecutionResultImpl.newExecutionResult().from(sourceResult);
        builder.addErrors(errorsEncountered);
        return builder.build();
    }

}
