package graphql.execution.instrumentation.parameters;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionInfo;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.schema.GraphQLFieldDefinition;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
public class InstrumentationFieldParameters {
    private final ExecutionContext executionContext;
    private final graphql.schema.GraphQLFieldDefinition fieldDef;
    private final ExecutionInfo executionInfo;
    private final InstrumentationState instrumentationState;

    public InstrumentationFieldParameters(ExecutionContext executionContext, GraphQLFieldDefinition fieldDef, ExecutionInfo executionInfo) {
        this(executionContext, fieldDef, executionInfo, executionContext.getInstrumentationState());
    }

    InstrumentationFieldParameters(ExecutionContext executionContext, GraphQLFieldDefinition fieldDef, ExecutionInfo executionInfo, InstrumentationState instrumentationState) {
        this.executionContext = executionContext;
        this.fieldDef = fieldDef;
        this.executionInfo = executionInfo;
        this.instrumentationState = instrumentationState;
    }

    /**
     * Returns a cloned parameters object with the new state
     *
     * @param instrumentationState the new state for this parameters object
     *
     * @return a new parameters object with the new state
     */
    public InstrumentationFieldParameters withNewState(InstrumentationState instrumentationState) {
        return new InstrumentationFieldParameters(
                this.executionContext, this.fieldDef, this.executionInfo, instrumentationState);
    }


    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    public GraphQLFieldDefinition getField() {
        return fieldDef;
    }

    public ExecutionInfo getExecutionInfo() {
        return executionInfo;
    }

    @SuppressWarnings("TypeParameterUnusedInFormals")
    public <T extends InstrumentationState> T getInstrumentationState() {
        //noinspection unchecked
        return (T) instrumentationState;
    }
}
