package graphql;

import graphql.execution.Async;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Internal
@NullMarked
public final class VariableBatchExecutionResultImpl extends ExecutionResultImpl implements VariableBatchExecutionResult {
    public VariableBatchExecutionResultImpl(List<CompletableFuture<ExecutionResult>> results) {
        super(List.copyOf(results), List.of());
    }

    @Override
    public List<CompletableFuture<ExecutionResult>> getResults() {
        return Assert.assertNotNull(getData());
    }

    @Override
    public int getSize() {
        return getResults().size();
    }

    @Override
    public CompletableFuture<List<ExecutionResult>> getResultsAsync() {
        return Async.<CompletableFuture<ExecutionResult>, ExecutionResult>each(getResults(), result -> result)
                .thenApply(List::copyOf);
    }

    @Override
    public Map<String, Object> toSpecification() {
        throw new UnsupportedOperationException("Serialize each execution result instead of the batch");
    }
}
