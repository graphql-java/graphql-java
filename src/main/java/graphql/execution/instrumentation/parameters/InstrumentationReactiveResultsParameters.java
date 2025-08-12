package graphql.execution.instrumentation.parameters;

import graphql.PublicApi;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.Instrumentation;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
@SuppressWarnings("TypeParameterUnusedInFormals")
@PublicApi
public class InstrumentationReactiveResultsParameters {

    /**
     * What type of reactive results was the {@link org.reactivestreams.Publisher}
     */
    public enum ResultType {
        DEFER, SUBSCRIPTION
    }

    private final ExecutionContext executionContext;
    private final ResultType resultType;

    public InstrumentationReactiveResultsParameters(ExecutionContext executionContext, ResultType resultType) {
        this.executionContext = executionContext;
        this.resultType = resultType;
    }


    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    public ResultType getResultType() {
        return resultType;
    }
}
