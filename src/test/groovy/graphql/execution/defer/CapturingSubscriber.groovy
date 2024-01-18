package graphql.execution.defer

import graphql.DeferredExecutionResult
import graphql.incremental.DeferPayload
import graphql.incremental.DelayedIncrementalExecutionResult
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

import java.util.concurrent.atomic.AtomicBoolean

class CapturingSubscriber implements Subscriber<DelayedIncrementalExecutionResult> {
    Subscription subscription
    AtomicBoolean finished = new AtomicBoolean()
    Throwable throwable
    List<DelayedIncrementalExecutionResult> executionResults = []
    List<Object> executionResultData = []

    AtomicBoolean subscribeTo(Publisher<DelayedIncrementalExecutionResult> publisher) {
        publisher.subscribe(this)
        return finished
    }

    @Override
    void onSubscribe(Subscription s) {
        assert s != null, "subscription must not be null"
        this.subscription = s
        s.request(1)
    }

    @Override
    void onNext(DelayedIncrementalExecutionResult incrementalExecutionResult) {
        executionResults.add(incrementalExecutionResult)
        executionResultData.addAll(incrementalExecutionResult.incremental
                // We only support defer (and not stream) for now
                .collect {((DeferPayload) it).data}
        )
        subscription.request(1)
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
