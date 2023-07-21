package graphql.execution.instrumentation.parameters;

import graphql.ExecutionInput;
import graphql.PublicApi;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.schema.GraphQLSchema;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
@SuppressWarnings("TypeParameterUnusedInFormals")
@PublicApi
public class InstrumentationParseExecutableNormalizedOperation extends InstrumentationExecutionParameters {
    public InstrumentationParseExecutableNormalizedOperation(ExecutionInput executionInput, GraphQLSchema schema) {
        super(executionInput, schema);
    }
}
