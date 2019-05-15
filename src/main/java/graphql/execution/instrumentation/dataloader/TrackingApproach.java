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

    InstrumentationState createState(ExecutionId executionId);

    void dispatch();

    ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters);

    DeferredFieldInstrumentationContext beginDeferredField(InstrumentationDeferredFieldParameters parameters);

    InstrumentationContext<Object>  beginFieldFetch(InstrumentationFieldFetchParameters parameters);

    void removeTracking(ExecutionId executionId);
}
