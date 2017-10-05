package graphql.execution.pubsub;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A subscriber that captures each object for testing
 */
public class CapturingSubscriber<T> implements Subscriber<T> {
    private final List<T> events = new ArrayList<>();
    private final AtomicBoolean done = new AtomicBoolean();
    private Subscription subscription;
    private Throwable throwable;


    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }

    @Override
    public void onNext(T t) {
        events.add(t);
        subscription.request(1);
    }

    @Override
    public void onError(Throwable t) {
        this.throwable = t;
        done.set(true);
    }

    @Override
    public void onComplete() {
        done.set(true);
    }

    public List<T> getEvents() {
        return events;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public AtomicBoolean isDone() {
        return done;
    }
}
