package graphql.execution.instrumentation.dataloader;


import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;

@ExperimentalApi
@NullMarked
public final class DataLoaderDispatchingContextKeys {
    private DataLoaderDispatchingContextKeys() {
    }

    /**
     * In nano seconds, the batch window size for delayed DataLoaders.
     * That is for DataLoaders, that are not batched as part of the normal per level
     * dispatching, because they were created after the level was already dispatched.
     * <p>
     * Expect Long values
     * <p>
     * Default is 500_000 (0.5 ms)
     */
    public static final String DELAYED_DATA_LOADER_BATCH_WINDOW_SIZE_NANO_SECONDS = "__GJ_delayed_data_loader_batch_window_size_nano_seconds";

    /**
     * An instance of {@link DelayedDataLoaderDispatcherExecutorFactory} that is used to create the
     * {@link java.util.concurrent.ScheduledExecutorService} for the delayed DataLoader dispatching.
     * <p>
     * Default is one static executor thread pool with a single thread.
     */
    public static final String DELAYED_DATA_LOADER_DISPATCHING_EXECUTOR_FACTORY = "__GJ_delayed_data_loader_dispatching_executor_factory";


    /**
     * Enables the ability to chain DataLoader dispatching.
     * <p>
     * Because this requires that all DataLoaders are accessed via DataFetchingEnvironment.getLoader()
     * this is not completely backwards compatible and therefore disabled by default.
     * <p>
     * Expects a boolean value.
     */
    public static final String ENABLE_DATA_LOADER_CHAINING = "__GJ_enable_data_loader_chaining";
}
