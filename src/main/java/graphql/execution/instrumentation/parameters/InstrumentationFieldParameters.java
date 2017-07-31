package graphql.execution.instrumentation.parameters;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionTypeInfo;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.schema.GraphQLFieldDefinition;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
public class InstrumentationFieldParameters {
    private final ExecutionContext executionContext;
    private final graphql.schema.GraphQLFieldDefinition fieldDef;
    private final ExecutionTypeInfo typeInfo;
    private final InstrumentationState instrumentationState;

    public InstrumentationFieldParameters(ExecutionContext executionContext, GraphQLFieldDefinition fieldDef, ExecutionTypeInfo typeInfo) {
        this.executionContext = executionContext;
        this.instrumentationState = executionContext.getInstrumentationState();
        this.fieldDef = fieldDef;
        this.typeInfo = typeInfo;
    }

    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    public GraphQLFieldDefinition getField() {
        return fieldDef;
    }

    public ExecutionTypeInfo getTypeInfo() {
        return typeInfo;
    }

    public <T extends InstrumentationState> T getInstrumentationState() {
        //noinspection unchecked
        return (T) instrumentationState;
    }
}
