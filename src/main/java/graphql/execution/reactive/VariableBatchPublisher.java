package graphql.execution.reactive;

import graphql.Assert;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.Internal;
import graphql.VariableBatchResult;
import graphql.execution.ExecutionId;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatchingContextKeys;
import graphql.execution.instrumentation.dataloader.VariableBatchDataLoaderDispatcher;
import graphql.incremental.IncrementalExecutionResult;
import graphql.incremental.IncrementalExecutionResultImpl;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

@Internal
@NullMarked
public final class VariableBatchPublisher implements Publisher<VariableBatchResult>, Subscription {
    private final ExecutionInput input;
    private final ExecutionId batchId;
    private final int size;
    private final int concurrency;
    private final Function<ExecutionInput, CompletableFuture<ExecutionResult>> execute;
    private final VariableBatchDataLoaderDispatcher dispatcher;
    private final NonBlockingMutexExecutor mutex = new NonBlockingMutexExecutor();
    private final AtomicBoolean subscribed = new AtomicBoolean();
    private final List<ExecutionInput> entries = new ArrayList<>();
    private final Deque<VariableBatchResult> ready = new ArrayDeque<>();
    private @Nullable Subscriber<? super VariableBatchResult> subscriber;
    private @Nullable Throwable failure;
    private long demand;
    private int next;
    private int active;
    private volatile boolean cancelled;
    private boolean terminated;

    public VariableBatchPublisher(ExecutionInput input, ExecutionId batchId, int concurrency,
                                  Function<ExecutionInput, CompletableFuture<ExecutionResult>> execute) {
        this.input = input;
        this.batchId = batchId;
        this.size = input.getVariablesList().size();
        this.concurrency = concurrency;
        this.execute = execute;
        this.dispatcher = new VariableBatchDataLoaderDispatcher(input.getDataLoaderRegistry(), this::fail);
        input.getCancellationFuture().thenRun(() -> mutex.execute(() -> {
            cancelEntries();
            failure = new CancellationException("Variable batch cancelled");
            drain();
        }));
    }

    @Override
    public void subscribe(Subscriber<? super VariableBatchResult> subscriber) {
        Assert.assertNotNullWithNPE(subscriber, "subscriber must not be null");
        if (!subscribed.compareAndSet(false, true)) {
            subscriber.onSubscribe(RejectedBatchSubscription.INSTANCE);
            subscriber.onError(new IllegalStateException("Variable batch results support one subscriber"));
            return;
        }
        mutex.execute(() -> {
            this.subscriber = subscriber;
            subscriber.onSubscribe(this);
            drain();
        });
    }

    @Override
    public void request(long amount) {
        if (amount <= 0) {
            fail(new IllegalArgumentException("Reactive Streams demand must be positive"));
            return;
        }
        mutex.execute(() -> {
            long sum = demand + amount;
            demand = sum < 0 ? Long.MAX_VALUE : sum;
            drain();
        });
    }

    @Override
    public void cancel() {
        cancelled = true;
        mutex.execute(this::cancelEntries);
    }

    private void fail(Throwable failure) {
        mutex.execute(() -> {
            this.failure = failure;
            drain();
        });
    }

    private void drain() {
        if (cancelled || terminated || subscriber == null) {
            return;
        }
        if (failure != null) {
            terminated = true;
            cancelEntries();
            subscriber.onError(failure);
            return;
        }
        emitReady();
        if (cancelled) {
            return;
        }
        if (next == size && active == 0 && ready.isEmpty()) {
            terminated = true;
            subscriber.onComplete();
            return;
        }
        startWave();
    }

    private void emitReady() {
        while (!cancelled && demand > 0 && !ready.isEmpty()) {
            demand--;
            Assert.assertNotNull(subscriber).onNext(ready.removeFirst());
        }
    }

    private void startWave() {
        // Let every newly started entry enqueue its keys before flushing the shared registry.
        // Async continuations can request further dispatches without waiting for another entry to finish.
        dispatcher.pause();
        try {
            while (!cancelled && next < size && active + ready.size() < concurrency
                    && active + ready.size() < demand) {
                startEntry(next++);
            }
        } finally {
            dispatcher.resume();
        }
    }

    private void startEntry(int index) {
        ExecutionInput entry = input.newVariableBatchEntry(index, batchId);
        entry.getGraphQLContext().put(VariableBatchDataLoaderDispatcher.class, dispatcher);
        DataLoaderDispatchingContextKeys.setEnableDataLoaderChaining(entry.getGraphQLContext(), false);
        DataLoaderDispatchingContextKeys.setEnableDataLoaderExhaustedDispatching(entry.getGraphQLContext(), true);
        entries.add(entry);
        active++;
        try {
            execute.apply(entry).whenComplete((result, failure) -> mutex.execute(() -> finishEntry(index, result, failure)));
        } catch (Throwable failure) {
            mutex.execute(() -> finishEntry(index, null, failure));
        }
    }

    private void finishEntry(int index, @Nullable ExecutionResult result, @Nullable Throwable failure) {
        active--;
        if (cancelled || terminated) {
            return;
        }
        if (failure != null) {
            this.failure = failure;
        } else {
            ready.addLast(VariableBatchResult.newVariableBatchResult()
                    .variableIndex(index).executionResult(wrapIncremental(index, Assert.assertNotNull(result))).build());
        }
        drain();
    }

    private ExecutionResult wrapIncremental(int index, ExecutionResult result) {
        if (!(result instanceof IncrementalExecutionResult)) {
            return result;
        }
        IncrementalExecutionResult incremental = (IncrementalExecutionResult) result;
        return IncrementalExecutionResultImpl.fromIncrementalExecutionResult(incremental)
                .incrementalItemPublisher(new VariableBatchIncrementalPublisher(incremental.getIncrementalItemPublisher(), entries.get(index)))
                .build();
    }

    private void cancelEntries() {
        entries.forEach(ExecutionInput::cancel);
        ready.clear();
    }
}
