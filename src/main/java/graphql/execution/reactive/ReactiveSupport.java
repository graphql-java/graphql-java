package graphql.execution.reactive;

import graphql.DuckTyped;
import graphql.Internal;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This provides support for a DataFetcher to be able to
 * return a reactive streams {@link Publisher} or Java JDK {@link Flow.Publisher}
 * as a value, and it can be turned into a {@link CompletableFuture}
 * that we can get an async value from.
 */
@Internal
public class ReactiveSupport {

    @DuckTyped(shape = "CompletableFuture | Object")
    public static Object fetchedObject(Object fetchedObject) {
        if (fetchedObject instanceof Flow.Publisher) {
            return flowPublisherToCF((Flow.Publisher<?>) fetchedObject);
        }
        if (fetchedObject instanceof Publisher) {
            return reactivePublisherToCF((Publisher<?>) fetchedObject);
        }
        return fetchedObject;
    }

    private static CompletableFuture<Object> reactivePublisherToCF(Publisher<?> publisher) {
        ReactivePublisherToCompletableFuture<Object> cf = new ReactivePublisherToCompletableFuture<>();
        publisher.subscribe(cf);
        return cf;
    }

    private static CompletableFuture<Object> flowPublisherToCF(Flow.Publisher<?> publisher) {
        FlowPublisherToCompletableFuture<Object> cf = new FlowPublisherToCompletableFuture<>();
        publisher.subscribe(cf);
        return cf;
    }

    /**
     * The implementations between reactive Publishers and Flow.Publishers are almost exactly the same except the
     * subscription class is different.  So this is a common class that contains most of the common logic
     *
     * @param <T> for two
     * @param <S> for subscription
     */
    private static abstract class PublisherToCompletableFuture<T, S> extends CompletableFuture<T> {

        private final AtomicReference<S> subscriptionRef = new AtomicReference<>();

        abstract void subscriptionCancel(S s);

        @SuppressWarnings("SameParameterValue")
        abstract void subscriptionRequest(S s, long howMany);

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled) {
                S s = subscriptionRef.getAndSet(null);
                if (s != null) {
                    subscriptionCancel(s);
                }
            }
            return cancelled;
        }

        private boolean validateSubscription(S current, S next) {
            Objects.requireNonNull(next, "Subscription cannot be null");
            if (current != null) {
                subscriptionCancel(next);
                return false;
            }
            return true;
        }


        public void onSubscribeImpl(S s) {
            if (validateSubscription(subscriptionRef.getAndSet(s), s)) {
                subscriptionRequest(s, Long.MAX_VALUE);
            }
        }

        public void onNextImpl(T t) {
            S s = subscriptionRef.getAndSet(null);
            if (s != null) {
                complete(t);
                subscriptionCancel(s);
            }
        }

        public void onErrorImpl(Throwable t) {
            if (subscriptionRef.getAndSet(null) != null) {
                completeExceptionally(t);
            }
        }

        public void onCompleteImpl() {
            if (subscriptionRef.getAndSet(null) != null) {
                complete(null);
            }
        }
    }

    private static class ReactivePublisherToCompletableFuture<T> extends PublisherToCompletableFuture<T, Subscription> implements Subscriber<T> {

        @Override
        void subscriptionCancel(Subscription subscription) {
            subscription.cancel();
        }

        @Override
        void subscriptionRequest(Subscription subscription, long howMany) {
            subscription.request(howMany);
        }

        @Override
        public void onSubscribe(Subscription s) {
            onSubscribeImpl(s);
        }

        @Override
        public void onNext(T t) {
            onNextImpl(t);
        }

        @Override
        public void onError(Throwable t) {
            onErrorImpl(t);
        }

        @Override
        public void onComplete() {
            onCompleteImpl();
        }
    }

    private static class FlowPublisherToCompletableFuture<T> extends PublisherToCompletableFuture<T, Flow.Subscription> implements Flow.Subscriber<T> {

        @Override
        void subscriptionCancel(Flow.Subscription subscription) {
            subscription.cancel();
        }

        @Override
        void subscriptionRequest(Flow.Subscription subscription, long howMany) {
            subscription.request(howMany);
        }

        @Override
        public void onSubscribe(Flow.Subscription s) {
            onSubscribeImpl(s);
        }

        @Override
        public void onNext(T t) {
            onNextImpl(t);
        }

        @Override
        public void onError(Throwable t) {
            onErrorImpl(t);
        }

        @Override
        public void onComplete() {
            onCompleteImpl();
        }
    }
}
