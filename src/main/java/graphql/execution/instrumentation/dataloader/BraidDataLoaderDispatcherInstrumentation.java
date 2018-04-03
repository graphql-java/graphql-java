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
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;

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
        final DispatchedBatchLoaders allDispatched = dataLoaderRegistry.getKeys().stream()
                .map(key -> dispatchBatchLoader(dataLoaderRegistry, key))
                .reduce(new DispatchedBatchLoaders(), DispatchedBatchLoaders::add, DispatchedBatchLoaders::combine);

        if (allDispatched.depth > 0) {
            allDispatched.whenComplete(this::dispatch);
        }
    }

    private DispatchedBatchLoader dispatchBatchLoader(DataLoaderRegistry dataLoaderRegistry, String key) {
        final DataLoader<Object, DataFetcherResult> dataLoader = dataLoaderRegistry.getDataLoader(key);
        final int dispatchDepth = dataLoader.dispatchDepth();

        return new DispatchedBatchLoader<>(key,
                dispatchDepth,
                dispatchDepth > 0 ? dataLoader.dispatch() : completedFuture(emptyList()));
    }

    private static class DispatchedBatchLoader<V> {
        private final String key;
        private final int depth;
        private final CompletableFuture<List<V>> futures;

        private DispatchedBatchLoader(String key, int depth, CompletableFuture<List<V>> futures) {
            this.key = key;
            this.depth = depth;
            this.futures = futures;
        }
    }

    private static class DispatchedBatchLoaders {
        private final int depth;
        private final List<CompletableFuture<List<?>>> futures;

        private DispatchedBatchLoaders() {
            this(0);
        }

        private DispatchedBatchLoaders(int depth) {
            this.depth = depth;
            this.futures = new ArrayList<>();
        }

        private DispatchedBatchLoaders(int depth, List<CompletableFuture<List<?>>> futures) {
            this.depth = depth;
            this.futures = new ArrayList<>(futures);
        }

        private DispatchedBatchLoaders add(DispatchedBatchLoader dbl) {
            return new DispatchedBatchLoaders(this.depth + dbl.depth, concat(this.futures, dbl.futures));
        }

        private static DispatchedBatchLoaders combine(DispatchedBatchLoaders ds1, DispatchedBatchLoaders ds2) {
            return new DispatchedBatchLoaders(ds1.depth + ds2.depth, concat(ds1.futures, ds2.futures));
        }

        void whenComplete(Runnable run) {
            CompletableFutureKit.allOf(futures).whenComplete((__, ___) -> run.run());
        }
    }

    static <T> List<T> concat(List<T> l, T t) {
        return concat(l, singletonList(t));
    }

    static <T> List<T> concat(List<T> l1, List<T> l2) {
        ArrayList<T> l = new ArrayList<>(l1);
        l.addAll(l2);
        l.trimToSize();
        return l;
    }

}
