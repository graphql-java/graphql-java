package graphql.execution.instrumentation.parameters;

import graphql.PublicApi;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;

/**
 * Parameters sent to {@link graphql.execution.instrumentation.Instrumentation} methods
 */
@PublicApi
public class InstrumentationExecutionStrategyParameters {

    private final ExecutionContext executionContext;
    private final ExecutionStrategyParameters executionStrategyParameters;
    private final InstrumentationState instrumentationState;

    public InstrumentationExecutionStrategyParameters(ExecutionContext executionContext, ExecutionStrategyParameters executionStrategyParameters) {
        this(executionContext, executionStrategyParameters, executionContext.getInstrumentationState());
    }

    private InstrumentationExecutionStrategyParameters(ExecutionContext executionContext, ExecutionStrategyParameters executionStrategyParameters, InstrumentationState instrumentationState) {
        this.executionContext = executionContext;
        this.executionStrategyParameters = executionStrategyParameters;
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
    public InstrumentationExecutionStrategyParameters withNewState(InstrumentationState instrumentationState) {
        return new InstrumentationExecutionStrategyParameters(executionContext, executionStrategyParameters, instrumentationState);
    }

    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    public ExecutionStrategyParameters getExecutionStrategyParameters() {
        return executionStrategyParameters;
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
    @SuppressWarnings("TypeParameterUnusedInFormals")
    public <T extends InstrumentationState> T getInstrumentationState() {
        //noinspection unchecked
        return (T) instrumentationState;
    }
}
