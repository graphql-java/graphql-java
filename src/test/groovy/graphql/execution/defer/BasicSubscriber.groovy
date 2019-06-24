package graphql.execution.defer

import graphql.DeferredExecutionResult
import graphql.ExecutionResult
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

import java.util.concurrent.atomic.AtomicBoolean

class BasicSubscriber implements Subscriber<DeferredExecutionResult> {
    Subscription subscription
    AtomicBoolean finished = new AtomicBoolean()
    Throwable throwable

    @Override
    void onSubscribe(Subscription s) {
        assert s != null, "subscription must not be null"
        this.subscription = s
        s.request(1)
    }

    @Override
    void onNext(DeferredExecutionResult executionResult) {
    }

    @Override
    void onError(Throwable t) {
        finished.set(true)
    }

    @Override
    void onComplete() {
        finished.set(true)
    }
}
