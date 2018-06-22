package graphql.execution.instrumentation.parameters;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.ExecutionTypeInfo;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.schema.GraphQLFieldDefinition;

/**
 * Parameters sent to {@link graphql.execution.instrumentation.Instrumentation} methods
 */
public class InstrumentationDeferredFieldParameters extends InstrumentationFieldParameters {

    private final ExecutionStrategyParameters executionStrategyParameters;

    public InstrumentationDeferredFieldParameters(ExecutionContext executionContext, ExecutionStrategyParameters executionStrategyParameters, GraphQLFieldDefinition fieldDef, ExecutionTypeInfo typeInfo) {
        this(executionContext, executionStrategyParameters, fieldDef, typeInfo, executionContext.getInstrumentationState());
    }

    InstrumentationDeferredFieldParameters(ExecutionContext executionContext, ExecutionStrategyParameters executionStrategyParameters, GraphQLFieldDefinition fieldDef, ExecutionTypeInfo typeInfo, InstrumentationState instrumentationState) {
        super(executionContext,fieldDef,typeInfo,instrumentationState);
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
                this.getExecutionContext(), this.executionStrategyParameters, this.getField(), this.getTypeInfo(), instrumentationState);
    }

    public ExecutionStrategyParameters getExecutionStrategyParameters() {
        return executionStrategyParameters;
    }
}
