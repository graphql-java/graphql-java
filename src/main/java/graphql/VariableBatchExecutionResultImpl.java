package graphql;

import org.jspecify.annotations.NullMarked;
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Internal
@NullMarked
public final class VariableBatchExecutionResultImpl extends ExecutionResultImpl implements VariableBatchExecutionResult {
    private final Publisher<VariableBatchResult> results;
    private final int size;

    public VariableBatchExecutionResultImpl(Publisher<VariableBatchResult> results, int size) {
        super(results, List.of());
        this.results = results;
        this.size = size;
    }

    @Override
    public Publisher<VariableBatchResult> getResults() {
        return results;
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public CompletableFuture<List<ExecutionResult>> getResultsAsync() {
        VariableBatchResultCollector collector = new VariableBatchResultCollector(size);
        results.subscribe(collector);
        return collector.getResult();
    }

    @Override
    public Map<String, Object> toSpecification() {
        throw new UnsupportedOperationException("Serialize each VariableBatchResult's executionResult instead of the batch");
    }
}
