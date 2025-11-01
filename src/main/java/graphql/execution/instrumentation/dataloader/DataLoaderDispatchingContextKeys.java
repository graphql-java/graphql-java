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
     * Enables the ability to chain DataLoader dispatching.
     * <p>
     * Because this requires that all DataLoaders are accessed via DataFetchingEnvironment.getLoader()
     * this is not completely backwards compatible and therefore disabled by default.
     * <p>
     * Expects a boolean value.
     */
    public static final String ENABLE_DATA_LOADER_CHAINING = "__GJ_enable_data_loader_chaining";


    /**
     * Enabled a different dispatching strategy that mimics the JS event loop based one:
     * DataLoader will be dispatched as soon as there is no data fetcher or batch loader currently running.
     *
     */
    public static final String ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING = "__GJ_enable_data_loader_exhausted_dispatching";

    /**
     * Enables the ability that chained DataLoaders are dispatched automatically.
     *
     * @param graphQLContext
     */
    public static void setEnableDataLoaderChaining(GraphQLContext graphQLContext, boolean enabled) {
        graphQLContext.put(ENABLE_DATA_LOADER_CHAINING, enabled);
    }

    /**
     * Enables the ability that chained DataLoaders are dispatched automatically.
     *
     * @param graphQLContext
     */
    public static void setEnableDataLoaderExhaustedDispatching(GraphQLContext graphQLContext, boolean enabled) {
        graphQLContext.put(ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING, enabled);
    }


}
