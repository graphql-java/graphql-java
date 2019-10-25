package graphql.execution.instrumentation.dataloader;

import graphql.Assert;
import graphql.Internal;
import graphql.execution.instrumentation.InstrumentationState;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.slf4j.Logger;

/**
 * A base class that keeps track of whether aggressive batching can be used
 */
public class DataLoaderDispatcherInstrumentationState implements InstrumentationState {

    @Internal
    public static final DataLoaderRegistry EMPTY_DATALOADER_REGISTRY = new DataLoaderRegistry() {
        @Override
        public DataLoaderRegistry register(String key, DataLoader<?, ?> dataLoader) {
            return Assert.assertShouldNeverHappen("You MUST set in your own DataLoaderRegistry to use data loader");
        }
    };

    private final FieldLevelTrackingApproach approach;
    private final DataLoaderRegistry dataLoaderRegistry;
    private final InstrumentationState state;
    private final boolean hasNoDataLoaders;
    private boolean aggressivelyBatching = true;

    public DataLoaderDispatcherInstrumentationState(Logger log, DataLoaderRegistry dataLoaderRegistry) {

        this.dataLoaderRegistry = dataLoaderRegistry;
        this.approach = new FieldLevelTrackingApproach(log, dataLoaderRegistry);
        this.state = approach.createState();
        //
        // if they have never set a dataloader into the execution input then we can optimize
        // away the tracking code
        //
        hasNoDataLoaders = dataLoaderRegistry == EMPTY_DATALOADER_REGISTRY;
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
        return dataLoaderRegistry;
    }

    boolean hasNoDataLoaders() {
        return hasNoDataLoaders;
    }

    InstrumentationState getState() {
        return state;
    }
}
