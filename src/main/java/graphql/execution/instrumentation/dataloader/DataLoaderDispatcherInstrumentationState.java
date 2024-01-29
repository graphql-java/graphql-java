package graphql.execution.instrumentation.dataloader;

import graphql.Assert;
import graphql.Internal;
import graphql.PublicApi;
import graphql.execution.instrumentation.InstrumentationState;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * A base class that keeps track of whether aggressive batching can be used
 */
@PublicApi
public class DataLoaderDispatcherInstrumentationState implements InstrumentationState {

    @Internal
    public static final DataLoaderRegistry EMPTY_DATALOADER_REGISTRY = new DataLoaderRegistry() {

        private static final String ERROR_MESSAGE = "You MUST set in your own DataLoaderRegistry to use data loader";

        @Override
        public DataLoaderRegistry register(String key, DataLoader<?, ?> dataLoader) {
            return Assert.assertShouldNeverHappen(ERROR_MESSAGE);
        }

        @Override
        public <K, V> DataLoader<K, V> computeIfAbsent(final String key,
                                                       final Function<String, DataLoader<?, ?>> mappingFunction) {
            return Assert.assertShouldNeverHappen(ERROR_MESSAGE);
        }

        @Override
        public DataLoaderRegistry unregister(String key) {
            return Assert.assertShouldNeverHappen(ERROR_MESSAGE);
        }
    };

    private final FieldLevelTrackingApproach approach;
    private final AtomicReference<DataLoaderRegistry> dataLoaderRegistry;
    private final InstrumentationState state;
    private volatile boolean aggressivelyBatching = true;
    private volatile boolean hasNoDataLoaders;

    public DataLoaderDispatcherInstrumentationState(DataLoaderRegistry dataLoaderRegistry) {
        this.dataLoaderRegistry = new AtomicReference<>(dataLoaderRegistry);
        this.approach = new FieldLevelTrackingApproach(this::getDataLoaderRegistry);
        this.state = approach.createState();
        hasNoDataLoaders = checkForNoDataLoader(dataLoaderRegistry);
    }

    private boolean checkForNoDataLoader(DataLoaderRegistry dataLoaderRegistry) {
        //
        // if they have never set a dataloader into the execution input then we can optimize
        // away the tracking code
        //
        return dataLoaderRegistry == EMPTY_DATALOADER_REGISTRY;
    }

    boolean isAggressivelyBatching() {
        return aggressivelyBatching;
    }

    void setAggressivelyBatching(boolean aggressivelyBatching) {
        this.aggressivelyBatching = aggressivelyBatching;
    }

    FieldLevelTrackingApproach getApproach() {
        return approach;
    }

    DataLoaderRegistry getDataLoaderRegistry() {
        return dataLoaderRegistry.get();
    }

    void setDataLoaderRegistry(DataLoaderRegistry newRegistry) {
        dataLoaderRegistry.set(newRegistry);
        hasNoDataLoaders = checkForNoDataLoader(newRegistry);
    }

    boolean hasNoDataLoaders() {
        return hasNoDataLoaders;
    }

    InstrumentationState getState() {
        return state;
    }
}
