package graphql.execution.instrumentation.parameters;

import graphql.PublicApi;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
@SuppressWarnings("TypeParameterUnusedInFormals")
@PublicApi
public class InstrumentationExecuteOperationParameters {
    private final ExecutionContext executionContext;
    private final InstrumentationState instrumentationState;

    public InstrumentationExecuteOperationParameters(ExecutionContext executionContext) {
        this(executionContext, executionContext.getInstrumentationState());
    }

    private InstrumentationExecuteOperationParameters(ExecutionContext executionContext, InstrumentationState instrumentationState) {
        this.executionContext = executionContext;
        this.instrumentationState = instrumentationState;
    }

    /**
     * Returns a cloned parameters object with the new state
     *
     * @param instrumentationState the new state for this parameters object
     *
     * @return a new parameters object with the new state
     *
     * @deprecated state is now passed in direct to instrumentation methods
     */
    @Deprecated(since = "2022-07-26")
    public InstrumentationExecuteOperationParameters withNewState(InstrumentationState instrumentationState) {
        return new InstrumentationExecuteOperationParameters(executionContext, instrumentationState);
    }

    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    /**
     * Previously the instrumentation parameters had access to the state created via {@link Instrumentation#createState(InstrumentationCreateStateParameters)} but now
     * to save object allocations, the state is passed directly into instrumentation methods
     *
     * @param <T> for two
     *
     * @return the state created previously during a call to {@link Instrumentation#createState(InstrumentationCreateStateParameters)}
     *
     * @deprecated state is now passed in direct to instrumentation methods
     */
    @Deprecated(since = "2022-07-26")
    public <T extends InstrumentationState> T getInstrumentationState() {
        //noinspection unchecked
        return (T) instrumentationState;
    }
}
