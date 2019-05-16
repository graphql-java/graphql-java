package graphql.execution.instrumentation.dataloader;

import graphql.execution.ExecutionId;
import graphql.execution.instrumentation.InstrumentationState;
import org.dataloader.DataLoaderRegistry;
import org.slf4j.Logger;

/**
 * A base class that keeps track of whether aggressive batching can be used
 */
public class DataLoaderDispatcherInstrumentationState implements InstrumentationState {

    private final TrackingApproach approach;
    private final DataLoaderRegistry dataLoaderRegistry;
    private final InstrumentationState state;
    private final boolean hasNoDataLoaders;
    private boolean aggressivelyBatching = true;

    public DataLoaderDispatcherInstrumentationState(DataLoaderRegistry dataLoaderRegistry,
                                                    DataLoaderDispatcherInstrumentationOptions options, ExecutionId executionId) {

        this.dataLoaderRegistry = dataLoaderRegistry;
        this.approach = options.getApproach(dataLoaderRegistry);
        this.state = approach.createState(executionId);
        hasNoDataLoaders = dataLoaderRegistry.getKeys().isEmpty();

    }

    boolean isAggressivelyBatching() {
        return aggressivelyBatching;
    }

    void setAggressivelyBatching(boolean aggressivelyBatching) {
        this.aggressivelyBatching = aggressivelyBatching;
    }

    TrackingApproach getApproach() {
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
