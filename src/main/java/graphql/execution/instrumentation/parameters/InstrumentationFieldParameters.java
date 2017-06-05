package graphql.execution.instrumentation.parameters;

import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
public class InstrumentationFieldParameters {
    private final ExecutionContext executionContext;
    private final graphql.schema.GraphQLFieldDefinition fieldDef;
    private final DataFetchingEnvironment environment;

    public InstrumentationFieldParameters(ExecutionContext executionContext, GraphQLFieldDefinition fieldDef, DataFetchingEnvironment environment) {
        this.executionContext = executionContext;
        this.fieldDef = fieldDef;
        this.environment = environment;
    }

    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    public GraphQLFieldDefinition getField() {
        return fieldDef;
    }

    public DataFetchingEnvironment getEnvironment() {
        return environment;
    }
}
