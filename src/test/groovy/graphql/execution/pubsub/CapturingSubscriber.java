package graphql.execution.pubsub;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A subscriber that captures each object for testing
 */
public class CapturingSubscriber<T> implements Subscriber<T> {
    private final List<T> events = new ArrayList<>();
    private final AtomicBoolean done = new AtomicBoolean();
    private final AtomicLong creationTime = new AtomicLong(System.nanoTime());
    private final int requestN;
    private Subscription subscription;
    private Throwable throwable;

    public CapturingSubscriber() {
        this(1);
    }

    public CapturingSubscriber(int requestN) {
        this.requestN = requestN;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        System.out.println("onSubscribe called at " + delta());
        this.subscription = subscription;
        subscription.request(requestN);
    }

    @Override
    public void onNext(T t) {
        System.out.println("\tonNext " + t + " called at " + delta());
        synchronized (this) {
            events.add(t);
            subscription.request(requestN);
        }
    }

    @Override
    public void onError(Throwable t) {
        System.out.println("onError called at " + delta());
        this.throwable = t;
        done.set(true);
    }

    @Override
    public void onComplete() {
        System.out.println("onComplete called at " + delta());
        done.set(true);
    }

    private String delta() {
        Duration nanos = Duration.ofNanos(System.nanoTime() - creationTime.get());
        return "+" + nanos.toMillis() + "ms";
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

    public boolean isCompleted() {
        return done.get() && throwable == null;
    }
    public boolean isCompletedExceptionally() {
        return done.get() && throwable != null;
    }

    public Subscription getSubscription() {
        return subscription;
    }

}
