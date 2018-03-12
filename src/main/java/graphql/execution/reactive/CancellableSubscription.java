package graphql.execution.reactive;

import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicBoolean;

public class CancellableSubscription implements Subscription {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    @Override
    public void request(long n) {
    }

    @Override
    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}
