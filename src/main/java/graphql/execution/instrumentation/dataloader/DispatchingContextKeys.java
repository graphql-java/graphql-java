package graphql.execution.instrumentation.dataloader;


import graphql.ExperimentalApi;

@ExperimentalApi
public final class DispatchingContextKeys {
    private DispatchingContextKeys() {
    }

    /**
     * In nano seconds, the batch window size for delayed DataLoaders.
     * That is for DataLoaders, that are not batched as part of the normal per level
     * dispatching, because they were created after the level was already dispatched.
     */
    public static final String BATCH_WINDOW_DELAYED_DL_NANO_SECONDS = "__batch_window_delayed_dl_nano_seconds";
}
