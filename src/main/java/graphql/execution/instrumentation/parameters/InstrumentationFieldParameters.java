package graphql.execution.instrumentation.parameters;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionPath;
import graphql.execution.TypeInfo;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.GraphQLFieldDefinition;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
public class InstrumentationFieldParameters {
    private final ExecutionContext executionContext;
    private final graphql.schema.GraphQLFieldDefinition fieldDef;
    private final TypeInfo typeInfo;
    private final ExecutionPath executionPath;

    public InstrumentationFieldParameters(ExecutionContext executionContext, GraphQLFieldDefinition fieldDef, TypeInfo typeInfo, ExecutionPath executionPath) {
        this.executionContext = executionContext;
        this.fieldDef = fieldDef;
        this.typeInfo = typeInfo;
        this.executionPath = executionPath;
    }

    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    public GraphQLFieldDefinition getField() {
        return fieldDef;
    }

    public TypeInfo getTypeInfo() {
        return typeInfo;
    }

    public ExecutionPath getExecutionPath() {
        return executionPath;
    }
}
