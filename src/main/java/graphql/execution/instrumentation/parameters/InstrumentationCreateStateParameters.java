package graphql.execution.instrumentation.parameters;

import graphql.ExecutionInput;
import graphql.PublicApi;
import graphql.schema.GraphQLSchema;
import org.jspecify.annotations.NullMarked;

/**
 * Parameters sent to {@link graphql.execution.instrumentation.Instrumentation} methods
 */
@NullMarked
@PublicApi
public class InstrumentationCreateStateParameters {
    private final GraphQLSchema schema;
    private final ExecutionInput executionInput;

    public InstrumentationCreateStateParameters(GraphQLSchema schema, ExecutionInput executionInput) {
        this.schema = schema;
        this.executionInput = executionInput;
    }

    public GraphQLSchema getSchema() {
        return schema;
    }

    public ExecutionInput getExecutionInput() {
        return executionInput;
    }
}
