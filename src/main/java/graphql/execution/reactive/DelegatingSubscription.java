package graphql.execution.reactive;

import graphql.PublicApi;
import org.reactivestreams.Subscription;

import static graphql.Assert.assertNotNull;

/**
 * A simple subscription that delegates to another
 */
@PublicApi
public class DelegatingSubscription implements Subscription {
    private final Subscription upstreamSubscription;

    public DelegatingSubscription(Subscription upstreamSubscription) {
        this.upstreamSubscription = assertNotNull(upstreamSubscription);
    }

    @Override
    public void request(long n) {
        upstreamSubscription.request(n);
    }

    @Override
    public void cancel() {
        upstreamSubscription.cancel();
    }

    /**
     * Get instance of upstreamSubscription
     * @return {@link Subscription}
     */
    public Subscription getUpstreamSubscription() {
        return upstreamSubscription;
    }
}
