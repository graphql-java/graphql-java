package graphql.execution.instrumentation.dataloader;

import graphql.ExecutionResult;
import graphql.execution.DataFetcherResult;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.impl.CompletableFutureKit;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static graphql.execution.instrumentation.SimpleInstrumentationContext.whenDispatched;
import static graphql.util.FpKit.concat;
import static java.util.Collections.emptyList;

/**
 * This approach uses a lazy approach where is waits until the last moment only calls dispatch when the query
 * is has been initially dispatched.
 *
 * It captures each dataloader future and async waits for them all the complete and then if there are more
 * outstanding data loader futures after that, it dispatches again and repeats this process until there are
 * zero outstanding calls left.
 */
public class CombinedCallsApproach {
    private final DataLoaderRegistry dataLoaderRegistry;
    private final Logger log;

    public CombinedCallsApproach(Logger log, DataLoaderRegistry dataLoaderRegistry) {
        this.dataLoaderRegistry = dataLoaderRegistry;
        this.log = log;
    }

    public InstrumentationState createState() {
        return new DataLoaderDispatcherInstrumentationState();
    }

    public InstrumentationContext<ExecutionResult> beginExecuteOperation() {
        return whenDispatched(__ -> dispatch());
    }

    public InstrumentationContext<ExecutionResult> beginDeferredField() {
        return whenDispatched(__ -> dispatch());
    }


    void dispatch() {
        log.debug("Dispatching all data loaders ({})", dataLoaderRegistry.getKeys());
        final CombinedDataLoaderDispatchCalls combinedCalls = dataLoaderRegistry.getKeys().stream()
                .map(key -> dispatchBatchLoader(dataLoaderRegistry, key))
                .reduce(new CombinedDataLoaderDispatchCalls(), CombinedDataLoaderDispatchCalls::add, CombinedDataLoaderDispatchCalls::combine);

        if (combinedCalls.depth > 0) {
            combinedCalls.whenComplete(this::dispatch);
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

        private static CombinedDataLoaderDispatchCalls combine(CombinedDataLoaderDispatchCalls combinedCalls1,
                                                               CombinedDataLoaderDispatchCalls combinedCalls2) {
            return new CombinedDataLoaderDispatchCalls(
                    combinedCalls1.depth + combinedCalls2.depth,
                    concat(combinedCalls1.futures, combinedCalls2.futures));
        }

        void whenComplete(Runnable run) {
            CompletableFutureKit.allOf(futures).whenComplete((__, ___) -> run.run());
        }
    }
}
