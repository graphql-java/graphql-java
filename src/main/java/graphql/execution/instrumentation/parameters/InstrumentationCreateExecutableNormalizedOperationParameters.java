package graphql.execution.instrumentation.parameters;

import graphql.ExecutionInput;
import graphql.ExperimentalApi;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.GraphQLSchema;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
@SuppressWarnings("TypeParameterUnusedInFormals")
@ExperimentalApi
public class InstrumentationCreateExecutableNormalizedOperationParameters extends InstrumentationExecutionParameters {
    public InstrumentationCreateExecutableNormalizedOperationParameters(ExecutionInput executionInput, GraphQLSchema schema) {
        super(executionInput, schema);
    }
}
