package graphql.execution.instrumentation.dataloader;

import graphql.execution.instrumentation.InstrumentationState;
import org.dataloader.DataLoaderRegistry;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A base class that keeps track of whether aggressive batching can be used
 */
public class DataLoaderDispatcherInstrumentationState implements InstrumentationState {

    private final FieldLevelTrackingApproach approach;
    private final AtomicReference<DataLoaderRegistry> dataLoaderRegistry;
    private final InstrumentationState state;
    private boolean aggressivelyBatching = true;

    public DataLoaderDispatcherInstrumentationState(Logger log, DataLoaderRegistry dataLoaderRegistry) {

        this.dataLoaderRegistry = new AtomicReference<>(dataLoaderRegistry);
        this.approach = new FieldLevelTrackingApproach(log, this::getDataLoaderRegistry);
        this.state = approach.createState();
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
    }

    boolean hasNoDataLoaders() {
        return getDataLoaderRegistry().getKeys().isEmpty();
    }

    InstrumentationState getState() {
        return state;
    }


}
