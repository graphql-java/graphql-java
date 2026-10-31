package graphql;

import org.jspecify.annotations.NullMarked;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Internal
@NullMarked
public final class VariableBatchResultCollector implements Subscriber<VariableBatchResult> {
    private final List<ExecutionResult> results;
    private final CompletableFuture<List<ExecutionResult>> result = new CompletableFuture<>();

    public VariableBatchResultCollector(int size) {
        results = new ArrayList<>(Collections.nCopies(size, new ExecutionResultImpl(List.of())));
    }

    public CompletableFuture<List<ExecutionResult>> getResult() {
        return result;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        result.whenComplete((ignored, failure) -> {
            if (result.isCancelled()) {
                subscription.cancel();
            }
        });
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(VariableBatchResult value) {
        results.set(value.getVariableIndex(), value.getExecutionResult());
    }

    @Override
    public void onError(Throwable failure) {
        result.completeExceptionally(failure);
    }

    @Override
    public void onComplete() {
        result.complete(List.copyOf(results));
    }
}
