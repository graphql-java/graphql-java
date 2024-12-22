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

        abstract void doSubscriptionCancel(S s);

        @SuppressWarnings("SameParameterValue")
        abstract void doSubscriptionRequest(S s, long n);

        private boolean validateSubscription(S current, S next) {
            Objects.requireNonNull(next, "Subscription cannot be null");
            if (current != null) {
                doSubscriptionCancel(next);
                return false;
            }
            return true;
        }

        /**
         * This overrides the {@link CompletableFuture#cancel(boolean)} method
         * such that subscription is also cancelled.
         *
         * @param mayInterruptIfRunning this value has no effect in this
         *                              implementation because interrupts are not used to control
         *                              processing.
         * @return a boolean if it was cancelled
         */
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled) {
                S s = subscriptionRef.getAndSet(null);
                if (s != null) {
                    doSubscriptionCancel(s);
                }
            }
            return cancelled;
        }

        void onSubscribeImpl(S s) {
            if (validateSubscription(subscriptionRef.getAndSet(s), s)) {
                doSubscriptionRequest(s, Long.MAX_VALUE);
            }
        }

        void onNextImpl(T t) {
            S s = subscriptionRef.getAndSet(null);
            if (s != null) {
                complete(t);
                doSubscriptionCancel(s);
            }
        }

        void onErrorImpl(Throwable t) {
            if (subscriptionRef.getAndSet(null) != null) {
                completeExceptionally(t);
            }
        }

        void onCompleteImpl() {
            if (subscriptionRef.getAndSet(null) != null) {
                complete(null);
            }
        }
    }

    private static class ReactivePublisherToCompletableFuture<T> extends PublisherToCompletableFuture<T, Subscription> implements Subscriber<T> {

        @Override
        void doSubscriptionCancel(Subscription subscription) {
            subscription.cancel();
        }

        @Override
        void doSubscriptionRequest(Subscription subscription, long n) {
            subscription.request(n);
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
        void doSubscriptionCancel(Flow.Subscription subscription) {
            subscription.cancel();
        }

        @Override
        void doSubscriptionRequest(Flow.Subscription subscription, long n) {
            subscription.request(n);
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
