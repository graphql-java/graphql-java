package graphql.execution.instrumentation.parameters;

import graphql.PublicApi;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.schema.GraphQLFieldDefinition;

import java.util.function.Supplier;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
@PublicApi
public class InstrumentationFieldParameters {
    private final ExecutionContext executionContext;
    private final Supplier<ExecutionStepInfo> executionStepInfo;
    public InstrumentationFieldParameters(ExecutionContext executionContext, Supplier<ExecutionStepInfo> executionStepInfo) {
        this.executionContext = executionContext;
        this.executionStepInfo = executionStepInfo;
    }
    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    public GraphQLFieldDefinition getField() {
        return executionStepInfo.get().getFieldDefinition();
    }

    public ExecutionStepInfo getExecutionStepInfo() {
        return executionStepInfo.get();
    }

}
