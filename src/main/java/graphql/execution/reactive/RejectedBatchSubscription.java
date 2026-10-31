package graphql.execution.reactive;

import graphql.Internal;
import org.reactivestreams.Subscription;

@Internal
public enum RejectedBatchSubscription implements Subscription {
    INSTANCE;

    @Override
    public void request(long amount) {
    }

    @Override
    public void cancel() {
    }
}
