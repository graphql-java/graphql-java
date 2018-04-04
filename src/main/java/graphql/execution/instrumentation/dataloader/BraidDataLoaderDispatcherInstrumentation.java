package graphql.execution.instrumentation.dataloader;

import graphql.ExecutionResult;
import graphql.execution.DataFetcherResult;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.impl.CompletableFutureKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static graphql.execution.instrumentation.SimpleInstrumentationContext.whenDispatched;
import static graphql.util.FpKit.concat;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

public final class BraidDataLoaderDispatcherInstrumentation extends SimpleInstrumentation {
    private static final Logger log = LoggerFactory.getLogger(BraidDataLoaderDispatcherInstrumentation.class);

    private final DataLoaderRegistry dataLoaderRegistry;

    public BraidDataLoaderDispatcherInstrumentation(DataLoaderRegistry dataLoaderRegistry) {
        this.dataLoaderRegistry = requireNonNull(dataLoaderRegistry);
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters) {
        return whenDispatched(__ -> dispatch());
    }

    private void dispatch() {
        log.debug("Dispatching all data loaders ({})", dataLoaderRegistry.getKeys());
        final CombinedDataLoaderDispatchCalls allDispatched = dataLoaderRegistry.getKeys().stream()
                .map(key -> dispatchBatchLoader(dataLoaderRegistry, key))
                .reduce(new CombinedDataLoaderDispatchCalls(), CombinedDataLoaderDispatchCalls::add, CombinedDataLoaderDispatchCalls::combine);

        if (allDispatched.depth > 0) {
            allDispatched.whenComplete(this::dispatch);
        }
    }

    private DataLoaderDispatchCall dispatchBatchLoader(DataLoaderRegistry dataLoaderRegistry, String key) {
        final DataLoader<Object, DataFetcherResult> dataLoader = dataLoaderRegistry.getDataLoader(key);
        return new DataLoaderDispatchCall<>(dataLoader.dispatchDepth(), dataLoader.dispatch());
    }

    private static class DataLoaderDispatchCall<V> {
        private final int depth;
        private final CompletableFuture<List<V>> futures;

        private DataLoaderDispatchCall(int depth, CompletableFuture<List<V>> futures) {
            this.depth = depth;
            this.futures = futures;
        }
    }

    private static class CombinedDataLoaderDispatchCalls {
        private final int depth;
        private final List<CompletableFuture<List<?>>> futures;

        private CombinedDataLoaderDispatchCalls() {
            this(0, emptyList());
        }

        private CombinedDataLoaderDispatchCalls(int depth, List<CompletableFuture<List<?>>> futures) {
            this.depth = depth;
            this.futures = new ArrayList<>(futures);
        }

        @SuppressWarnings("unchecked")
        private CombinedDataLoaderDispatchCalls add(DataLoaderDispatchCall ddc) {
            return new CombinedDataLoaderDispatchCalls(this.depth + ddc.depth, concat(this.futures, ddc.futures));
        }

        private static CombinedDataLoaderDispatchCalls combine(CombinedDataLoaderDispatchCalls cdldc1,
                                                               CombinedDataLoaderDispatchCalls cdldc2) {
            return new CombinedDataLoaderDispatchCalls(
                    cdldc1.depth + cdldc2.depth,
                    concat(cdldc1.futures, cdldc2.futures));
        }

        void whenComplete(Runnable run) {
            CompletableFutureKit.allOf(futures).whenComplete((__, ___) -> run.run());
        }
    }
}
