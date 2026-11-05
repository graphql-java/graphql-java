package graphql.execution.instrumentation.dataloader;


import graphql.GraphQLContext;
import graphql.Internal;
import org.jspecify.annotations.NullMarked;

/**
 * GraphQLContext keys related to DataLoader dispatching.
 */
@Internal
@NullMarked
public final class DataLoaderDispatchingContextKeys {
    private DataLoaderDispatchingContextKeys() {
    }

    /**
     * Enables chained DataLoader dispatching using the per-level strategy.
     * <p>
     * This is disabled by default. Enabling it selects per-level dispatching unless exhausted dispatching
     * is explicitly configured. Explicitly enabling both strategies is invalid.
     * DataLoaders must be accessed via {@link graphql.schema.DataFetchingEnvironment#getDataLoader(String)}.
     * The default exhausted strategy supports chaining independently of this setting.
     * <p>
     * Expects a boolean value.
     */
    public static final String ENABLE_DATA_LOADER_CHAINING = "__GJ_enable_data_loader_chaining";


    /**
     * Enables a dispatching strategy that mimics the JS event loop based one:
     * DataLoader will be dispatched as soon as there is no data fetcher or batch loader currently running.
     * Since version 27, this defaults to true unless {@link #ENABLE_DATA_LOADER_CHAINING} is true.
     * Set this to false to use per-level dispatching. DataLoaders must be accessed via
     * {@link graphql.schema.DataFetchingEnvironment#getDataLoader(String)} so their load calls can be tracked.
     */
    public static final String ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING = "__GJ_enable_data_loader_exhausted_dispatching";

    /**
     * Enables automatic dispatching of chained DataLoaders using the per-level strategy.
     *
     * @param graphQLContext the execution context
     * @param enabled whether to enable per-level chained dispatching
     */
    public static void setEnableDataLoaderChaining(GraphQLContext graphQLContext, boolean enabled) {
        graphQLContext.put(ENABLE_DATA_LOADER_CHAINING, enabled);
    }

    /**
     * Configures exhausted dispatching, which is the default since version 27 unless per-level chained
     * dispatching is explicitly enabled. Set this to false to use per-level dispatching.
     *
     * @param graphQLContext the execution context
     * @param enabled whether to use exhausted dispatching
     */
    public static void setEnableDataLoaderExhaustedDispatching(GraphQLContext graphQLContext, boolean enabled) {
        graphQLContext.put(ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING, enabled);
    }


}
