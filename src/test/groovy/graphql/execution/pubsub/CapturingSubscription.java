package graphql.execution.pubsub;

import org.reactivestreams.Subscription;

public class CapturingSubscription implements Subscription {
    private long count = 0;
    private boolean cancelled = false;

    public long getCount() {
        return count;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void request(long l) {
        count += l;
    }

    @Override
    public void cancel() {
        cancelled = true;
    }
}
