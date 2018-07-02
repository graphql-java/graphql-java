package graphql.execution.instrumentation.parameters;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
public class InstrumentationFieldFetchParameters extends InstrumentationFieldParameters {
    private final DataFetchingEnvironment environment;
    private final ExecutionStrategyParameters executionStrategyParameters;

    public InstrumentationFieldFetchParameters(ExecutionContext getExecutionContext, GraphQLFieldDefinition fieldDef, DataFetchingEnvironment environment, ExecutionStrategyParameters executionStrategyParameters) {
        super(getExecutionContext, fieldDef, environment.getFieldTypeInfo());
        this.environment = environment;
        this.executionStrategyParameters = executionStrategyParameters;
    }

    private InstrumentationFieldFetchParameters(ExecutionContext getExecutionContext, GraphQLFieldDefinition fieldDef, DataFetchingEnvironment environment, InstrumentationState instrumentationState, ExecutionStrategyParameters executionStrategyParameters) {
        super(getExecutionContext, fieldDef, environment.getFieldTypeInfo(), instrumentationState);
        this.environment = environment;
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
    public InstrumentationFieldFetchParameters withNewState(InstrumentationState instrumentationState) {
        return new InstrumentationFieldFetchParameters(
                this.getExecutionContext(), this.getField(), this.getEnvironment(),
                instrumentationState, executionStrategyParameters);
    }


    public DataFetchingEnvironment getEnvironment() {
        return environment;
    }
}
