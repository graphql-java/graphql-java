package graphql.execution.instrumentation.parameters;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionPath;
import graphql.execution.TypeInfo;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
public class InstrumentationFieldFetchParameters extends InstrumentationFieldParameters {
    private final DataFetchingEnvironment environment;

    public InstrumentationFieldFetchParameters(ExecutionContext getExecutionContext, GraphQLFieldDefinition fieldDef, DataFetchingEnvironment environment, TypeInfo typeInfo, ExecutionPath executionPath) {
        super(getExecutionContext, fieldDef, typeInfo, executionPath);
        this.environment = environment;
    }

    public DataFetchingEnvironment getEnvironment() {
        return environment;
    }
}
