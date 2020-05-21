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
        System.out.println("onSubscribe called at " + System.nanoTime());
        this.subscription = subscription;
        subscription.request(1);
    }

    @Override
    public void onNext(T t) {
        System.out.println("onNext called at " + System.nanoTime());
        events.add(t);
        subscription.request(1);
    }

    @Override
    public void onError(Throwable t) {
        System.out.println("onError called at " + System.nanoTime());
        this.throwable = t;
        done.set(true);
    }

    @Override
    public void onComplete() {
        System.out.println("onComplete called at " + System.nanoTime());
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
