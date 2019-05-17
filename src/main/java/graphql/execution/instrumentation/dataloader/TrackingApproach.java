package graphql.execution.instrumentation.dataloader;

import graphql.execution.ExecutionId;
import graphql.execution.instrumentation.DeferredFieldInstrumentationContext;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.parameters.InstrumentationDeferredFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;

public interface TrackingApproach extends InstrumentationState {

    /**
     * Handles creating any state for DataLoaderDispatcherInstrumentation
     * @param executionId the execution to create state for.
     * @return individual state, if any for the execution.
     */
    InstrumentationState createState(ExecutionId executionId);

    /**
     * Dispatch dataloaders and clean up state.
     */
    void dispatch();

    /**
     * Handles approach specific logic for DataLoaderDispatcherInstrumentation.
     * @param parameters parameters supplied to DataLoaderDispatcherInstrumentation
     * @return the instrumented context
     */
    ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters);

    /**
     * Handles approach specific logic for DataLoaderDispatcherInstrumentation.
     * @param parameters parameters supplied to DataLoaderDispatcherInstrumentation
     * @return the instrumented context
     */
    DeferredFieldInstrumentationContext beginDeferredField(InstrumentationDeferredFieldParameters parameters);

    /**
     * Handles approach specific logic for DataLoaderDispatcherInstrumentation.
     * @param parameters parameters supplied to DataLoaderDispatcherInstrumentation
     * @return the instrumented context
     */
    InstrumentationContext<Object>  beginFieldFetch(InstrumentationFieldFetchParameters parameters);

    /**
     * Removes tracking state for an execution.
     * @param executionId the execution to remove state for
     */
    void removeTracking(ExecutionId executionId);
}
