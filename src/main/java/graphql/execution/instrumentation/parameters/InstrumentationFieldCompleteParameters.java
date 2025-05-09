package graphql.execution.instrumentation.parameters;

import graphql.PublicApi;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ExecutionStrategyParameters;
import graphql.schema.GraphQLFieldDefinition;

import java.util.function.Supplier;

/**
 * Parameters sent to {@link graphql.execution.instrumentation.Instrumentation} methods
 */
@PublicApi
public class InstrumentationFieldCompleteParameters {
    private final ExecutionContext executionContext;
    private final Supplier<ExecutionStepInfo> executionStepInfo;
    private final Object fetchedValue;
    private final ExecutionStrategyParameters executionStrategyParameters;

    public InstrumentationFieldCompleteParameters(ExecutionContext executionContext, ExecutionStrategyParameters executionStrategyParameters, Supplier<ExecutionStepInfo> executionStepInfo, Object fetchedValue) {
        this.executionContext = executionContext;
        this.executionStrategyParameters = executionStrategyParameters;
        this.executionStepInfo = executionStepInfo;
        this.fetchedValue = fetchedValue;
    }



    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    public ExecutionStrategyParameters getExecutionStrategyParameters() {
        return executionStrategyParameters;
    }

    public GraphQLFieldDefinition getField() {
        return getExecutionStepInfo().getFieldDefinition();
    }

    @Deprecated(since = "2020-09-08")
    public ExecutionStepInfo getTypeInfo() {
        return getExecutionStepInfo();
    }

    public ExecutionStepInfo getExecutionStepInfo() {
        return executionStepInfo.get();
    }

    /**
     * This returns the object that was fetched, ready to be completed as a value.  This can sometimes be a {@link graphql.execution.FetchedValue} object
     * but most often it's a simple POJO.
     *
     * @return the object was fetched read
     */
    public Object getFetchedObject() {
        return fetchedValue;
    }

}
