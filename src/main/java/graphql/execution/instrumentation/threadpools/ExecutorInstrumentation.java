package graphql.execution.instrumentation.threadpools;

import com.google.common.annotations.Beta;
import graphql.Assert;
import graphql.Internal;
import graphql.TrivialDataFetcher;
import graphql.execution.Async;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static graphql.execution.instrumentation.threadpools.ExecutorInstrumentation.Action.FETCHING;
import static graphql.execution.instrumentation.threadpools.ExecutorInstrumentation.Action.PROCESSING;

/**
 * This instrumentation can be used to control on what thread calls to {@link DataFetcher}s happen on.
 * <p>
 * If your data fetching is inherently IO bound then you could use a IO oriented thread pool for your fetches and transfer control
 * back to a CPU oriented thread pool and allow graphql-java code to run the post processing of results there.
 * <p>
 * An IO oriented thread pool is typically a multiple of {@link Runtime#availableProcessors()} while a CPU oriented thread pool
 * is typically no more than {@link Runtime#availableProcessors()}.
 * <p>
 * The instrumentation will use the {@link graphql.execution.instrumentation.Instrumentation#instrumentDataFetcher(DataFetcher, InstrumentationFieldFetchParameters)}
 * method to change your data fetchers so they are executed on a thread pool dedicated to fetching (if you provide one).
 * <p>
 * Once the data fetcher value is returns it will transfer control back to a processing thread pool (if you provide one).
 * <p>
 * This code uses {@link CompletableFuture#supplyAsync(Supplier, Executor)} and {@link CompletableFuture#thenApplyAsync(Function, Executor)} to transfer
 * control between thread pools.
 */
@Internal
@Beta
public class ExecutorInstrumentation extends SimpleInstrumentation {

    private static final Consumer<Action> NOOP = a -> {
    };

    /**
     * This describes what action is currently being done.  This is mostly intended for testing.
     */
    enum Action {FETCHING, PROCESSING}

    private final Executor fetchExecutor;
    private final Executor processingExecutor;
    private final Consumer<Action> actionObserver;

    private ExecutorInstrumentation(Executor fetchExecutor, Executor processingExecutor, Consumer<Action> actionObserver) {
        this.fetchExecutor = fetchExecutor;
        this.processingExecutor = processingExecutor;
        this.actionObserver = actionObserver;
    }

    public Executor getFetchExecutor() {
        return fetchExecutor;
    }

    public Executor getProcessingExecutor() {
        return processingExecutor;
    }

    public static Builder newThreadPoolExecutionInstrumentation() {
        return new Builder();
    }

    public static class Builder {
        Executor fetchExecutor;
        Executor processingExecutor;
        private Consumer<Action> actionObserver;

        public Builder fetchExecutor(Executor fetchExecutor) {
            this.fetchExecutor = fetchExecutor;
            return this;
        }

        public Builder processingExecutor(Executor processingExecutor) {
            this.processingExecutor = processingExecutor;
            return this;
        }

        /**
         * This is really intended for testing but this consumer will be called during
         * stages to indicate what is happening.
         *
         * @param actionObserver the observer code
         *
         * @return this builder
         */
        public Builder actionObserver(Consumer<Action> actionObserver) {
            this.actionObserver = Assert.assertNotNull(actionObserver);
            return this;
        }

        public ExecutorInstrumentation build() {
            return new ExecutorInstrumentation(fetchExecutor, processingExecutor, actionObserver != null ? actionObserver : NOOP);
        }

    }

    @Override
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> originalDataFetcher, InstrumentationFieldFetchParameters parameters) {
        if (originalDataFetcher instanceof TrivialDataFetcher) {
            return originalDataFetcher;
        }
        return environment -> {
            CompletableFuture<CompletionStage<?>> invokedCF;
            if (fetchExecutor != null) {
                // run the fetch asynchronously via the fetch executor
                // the CF will be left running on that fetch executors thread
                invokedCF = CompletableFuture.supplyAsync(invokedAsync(originalDataFetcher, environment), fetchExecutor);
            } else {
                invokedCF = invokedSynch(originalDataFetcher, environment);
            }
            if (processingExecutor != null) {
                invokedCF = invokedCF.thenApplyAsync(processingControl(), processingExecutor);
            } else {
                invokedCF = invokedCF.thenApply(processingControl());
            }
            return invokedCF.thenCompose(cs -> cs);
        };
    }


    private Supplier<CompletionStage<?>> invokedAsync(DataFetcher<?> originalDataFetcher, DataFetchingEnvironment environment) {
        return () -> {
            actionObserver.accept(FETCHING);
            return invokeOriginalDF(originalDataFetcher, environment);
        };
    }

    private CompletableFuture<CompletionStage<?>> invokedSynch(DataFetcher<?> originalDataFetcher, DataFetchingEnvironment environment) {
        actionObserver.accept(FETCHING);
        return CompletableFuture.completedFuture(invokeOriginalDF(originalDataFetcher, environment));
    }

    private Function<CompletionStage<?>, CompletionStage<?>> processingControl() {
        return completionStage -> {
            actionObserver.accept(PROCESSING);
            return completionStage;
        };
    }

    private CompletionStage<?> invokeOriginalDF(DataFetcher<?> originalDataFetcher, DataFetchingEnvironment environment) {
        Object value;
        try {
            value = originalDataFetcher.get(environment);
        } catch (Exception e) {
            return Async.exceptionallyCompletedFuture(e);
        }
        if (value instanceof CompletionStage) {
            return ((CompletionStage<?>) value);
        } else {
            return CompletableFuture.completedFuture(value);
        }
    }
}
