package graphql.execution.instrumentation.dataloader;

import graphql.PublicApi;
import graphql.execution.instrumentation.InstrumentationState;
import org.dataloader.DataLoaderRegistry;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A base class that keeps track of whether aggressive batching can be used
 */
@PublicApi
public class BatchInstrumentationState implements InstrumentationState {

    private final BatchTrackingApproach approach;
    private final AtomicReference<DataLoaderRegistry> dataLoaderRegistry;
    private final InstrumentationState state;

    public BatchInstrumentationState(DataLoaderRegistry dataLoaderRegistry) {
        this.dataLoaderRegistry = new AtomicReference<>(dataLoaderRegistry);
        this.approach = new BatchTrackingApproach(this::getDataLoaderRegistry);
        this.state = approach.createState();
    }


    BatchTrackingApproach getApproach() {
        return approach;
    }

    DataLoaderRegistry getDataLoaderRegistry() {
        return dataLoaderRegistry.get();
    }

    void setDataLoaderRegistry(DataLoaderRegistry newRegistry) {
        dataLoaderRegistry.set(newRegistry);
    }

    InstrumentationState getState() {
        return state;
    }
}
