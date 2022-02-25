package graphql.execution.instrumentation.parameters;

import graphql.PublicApi;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.schema.DataFetchingEnvironment;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
@PublicApi
public class InstrumentationFieldFetchParameters extends InstrumentationFieldParameters {
    private final DataFetchingEnvironment environment;
    private final boolean trivialDataFetcher;

    public InstrumentationFieldFetchParameters(ExecutionContext getExecutionContext, DataFetchingEnvironment environment, boolean trivialDataFetcher) {
        super(getExecutionContext, environment::getExecutionStepInfo);
        this.environment = environment;
        this.trivialDataFetcher = trivialDataFetcher;
    }

    public DataFetchingEnvironment getEnvironment() {
        return environment;
    }

    public boolean isTrivialDataFetcher() {
        return trivialDataFetcher;
    }
}
