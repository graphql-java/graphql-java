package graphql.execution.defer

import graphql.ExecutionResult
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

import java.util.concurrent.atomic.AtomicBoolean

class BasicSubscriber implements Subscriber<ExecutionResult> {
    Subscription subscription
    AtomicBoolean finished = new AtomicBoolean()
    Throwable throwable

    @Override
    void onSubscribe(Subscription s) {
        assert s != null
        this.subscription = s
        s.request(1)
    }

    @Override
    void onNext(ExecutionResult executionResult) {
    }

    @Override
    void onError(Throwable t) {
        def writer = new StringWriter()
        t.printStackTrace(new PrintWriter(writer))
        t.printStackTrace()
        assert false, "OnError was unexpectantly called with throwable : " + writer.toString()
    }

    @Override
    void onComplete() {
        finished.set(true)
    }
}
