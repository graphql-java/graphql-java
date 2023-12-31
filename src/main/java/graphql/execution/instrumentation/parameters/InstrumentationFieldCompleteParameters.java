package graphql.execution.instrumentation.parameters;

import graphql.PublicApi;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.schema.GraphQLFieldDefinition;

import java.util.function.Supplier;

/**
 * Parameters sent to {@link graphql.execution.instrumentation.Instrumentation} methods
 */
@PublicApi
public class InstrumentationFieldCompleteParameters {
    private final ExecutionContext executionContext;
    private final Supplier<ExecutionStepInfo> executionStepInfo;
    private final Object fetchedValue;
    private final InstrumentationState instrumentationState;
    private final ExecutionStrategyParameters executionStrategyParameters;

    public InstrumentationFieldCompleteParameters(ExecutionContext executionContext, ExecutionStrategyParameters executionStrategyParameters, Supplier<ExecutionStepInfo> executionStepInfo, Object fetchedValue) {
        this(executionContext, executionStrategyParameters, executionStepInfo, fetchedValue, executionContext.getInstrumentationState());
    }

    InstrumentationFieldCompleteParameters(ExecutionContext executionContext, ExecutionStrategyParameters executionStrategyParameters, Supplier<ExecutionStepInfo> executionStepInfo, Object fetchedValue, InstrumentationState instrumentationState) {
        this.executionContext = executionContext;
        this.executionStrategyParameters = executionStrategyParameters;
        this.executionStepInfo = executionStepInfo;
        this.fetchedValue = fetchedValue;
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
    public InstrumentationFieldCompleteParameters withNewState(InstrumentationState instrumentationState) {
        return new InstrumentationFieldCompleteParameters(
                this.executionContext, executionStrategyParameters, this.executionStepInfo, this.fetchedValue, instrumentationState);
    }


    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    public ExecutionStrategyParameters getExecutionStrategyParameters() {
        return executionStrategyParameters;
    }

    public GraphQLFieldDefinition getField() {
        return getExecutionStepInfo().getFieldDefinition();
    }

    @Deprecated(since = "2020-09-08")
    public ExecutionStepInfo getTypeInfo() {
        return getExecutionStepInfo();
    }

    public ExecutionStepInfo getExecutionStepInfo() {
        return executionStepInfo.get();
    }

    public Object getFetchedValue() {
        return fetchedValue;
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
