package graphql.execution.instrumentation.parameters;

import graphql.PublicApi;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
@SuppressWarnings("TypeParameterUnusedInFormals")
@PublicApi
public class InstrumentationExecuteOperationParameters {
    private final ExecutionContext executionContext;
    public InstrumentationExecuteOperationParameters(ExecutionContext executionContext) {
        this.executionContext = executionContext;
    }


    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

}
