package graphql.execution.reactive;

import graphql.Assert;
import graphql.ExecutionInput;
import graphql.Internal;
import graphql.incremental.DelayedIncrementalPartialResult;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

@Internal
@NullMarked
public final class VariableBatchIncrementalPublisher implements Publisher<DelayedIncrementalPartialResult>,
        Subscriber<DelayedIncrementalPartialResult>, Subscription {
    private final Publisher<DelayedIncrementalPartialResult> source;
    private final ExecutionInput input;
    private final NonBlockingMutexExecutor mutex = new NonBlockingMutexExecutor();
    private final AtomicBoolean subscribed = new AtomicBoolean();
    private @Nullable Subscriber<? super DelayedIncrementalPartialResult> downstream;
    private @Nullable Subscription upstream;
    private long demand;
    private boolean terminated;
    private volatile boolean cancelled;

    public VariableBatchIncrementalPublisher(Publisher<DelayedIncrementalPartialResult> source, ExecutionInput input) {
        this.source = source;
        this.input = input;
        input.getCancellationFuture().thenRun(() -> onError(new CancellationException("Variable batch entry cancelled")));
    }

    @Override
    public void subscribe(Subscriber<? super DelayedIncrementalPartialResult> subscriber) {
        Assert.assertNotNullWithNPE(subscriber, "subscriber must not be null");
        if (!subscribed.compareAndSet(false, true)) {
            subscriber.onSubscribe(RejectedBatchSubscription.INSTANCE);
            subscriber.onError(new IllegalStateException("Incremental results support one subscriber"));
            return;
        }
        mutex.execute(() -> subscribeImpl(subscriber));
    }

    private void subscribeImpl(Subscriber<? super DelayedIncrementalPartialResult> subscriber) {
        downstream = subscriber;
        subscriber.onSubscribe(this);
        if (cancelled) {
            return;
        }
        if (input.isCancelled()) {
            subscriber.onError(new CancellationException("Variable batch entry cancelled"));
            terminated = true;
            return;
        }
        try {
            source.subscribe(this);
        } catch (Throwable failure) {
            onError(failure);
        }
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        mutex.execute(() -> {
            if (cancelled || terminated || upstream != null) {
                subscription.cancel();
                return;
            }
            upstream = subscription;
            if (demand > 0) {
                subscription.request(demand);
            }
        });
    }

    @Override
    public void request(long amount) {
        if (amount <= 0) {
            onError(new IllegalArgumentException("Reactive Streams demand must be positive"));
            return;
        }
        mutex.execute(() -> {
            if (cancelled || terminated) {
                return;
            }
            long sum = demand + amount;
            demand = sum < 0 ? Long.MAX_VALUE : sum;
            if (upstream != null) {
                upstream.request(amount);
            }
        });
    }

    @Override
    public void cancel() {
        cancelled = true;
        input.cancel();
        mutex.execute(this::cancelUpstream);
    }

    private void cancelUpstream() {
        if (upstream != null) {
            upstream.cancel();
        }
    }

    @Override
    public void onNext(DelayedIncrementalPartialResult value) {
        mutex.execute(() -> {
            if (!cancelled && !terminated) {
                Assert.assertNotNull(downstream).onNext(value);
            }
        });
    }

    @Override
    public void onError(Throwable failure) {
        mutex.execute(() -> {
            if (cancelled || terminated || downstream == null) {
                return;
            }
            terminated = true;
            cancelUpstream();
            downstream.onError(failure);
        });
    }

    @Override
    public void onComplete() {
        mutex.execute(() -> {
            if (!cancelled && !terminated) {
                terminated = true;
                Assert.assertNotNull(downstream).onComplete();
            }
        });
    }
}
