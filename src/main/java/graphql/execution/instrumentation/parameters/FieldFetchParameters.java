package graphql.execution.instrumentation.parameters;

import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
public class FieldFetchParameters extends FieldParameters {
    private final DataFetchingEnvironment environment;

    public FieldFetchParameters(ExecutionContext getExecutionContext, GraphQLFieldDefinition fieldDef, DataFetchingEnvironment environment) {
        super(getExecutionContext, fieldDef);
        this.environment = environment;
    }

    public DataFetchingEnvironment getEnvironment() {
        return environment;
    }
}
