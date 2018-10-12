package graphql.execution.instrumentation.parameters;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionInfo;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.schema.GraphQLFieldDefinition;

/**
 * Parameters sent to {@link graphql.execution.instrumentation.Instrumentation} methods
 */
public class InstrumentationDeferredFieldParameters extends InstrumentationFieldParameters {

    private final ExecutionStrategyParameters executionStrategyParameters;

    public InstrumentationDeferredFieldParameters(ExecutionContext executionContext, ExecutionStrategyParameters executionStrategyParameters, GraphQLFieldDefinition fieldDef, ExecutionInfo executionInfo) {
        this(executionContext, executionStrategyParameters, fieldDef, executionInfo, executionContext.getInstrumentationState());
    }

    InstrumentationDeferredFieldParameters(ExecutionContext executionContext, ExecutionStrategyParameters executionStrategyParameters, GraphQLFieldDefinition fieldDef, ExecutionInfo executionInfo, InstrumentationState instrumentationState) {
        super(executionContext,fieldDef,executionInfo,instrumentationState);
        this.executionStrategyParameters = executionStrategyParameters;
    }

    /**
     * Returns a cloned parameters object with the new state
     *
     * @param instrumentationState the new state for this parameters object
     *
     * @return a new parameters object with the new state
     */
    @Override
    public InstrumentationDeferredFieldParameters withNewState(InstrumentationState instrumentationState) {
        return new InstrumentationDeferredFieldParameters(
                this.getExecutionContext(), this.executionStrategyParameters, this.getField(), this.getExecutionInfo(), instrumentationState);
    }

    public ExecutionStrategyParameters getExecutionStrategyParameters() {
        return executionStrategyParameters;
    }
}
