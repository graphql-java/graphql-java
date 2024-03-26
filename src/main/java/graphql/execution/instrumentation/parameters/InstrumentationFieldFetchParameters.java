package graphql.execution.instrumentation.parameters;

import graphql.PublicApi;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.schema.DataFetchingEnvironment;

import java.util.function.Supplier;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
@PublicApi
public class InstrumentationFieldFetchParameters extends InstrumentationFieldParameters {
    private final Supplier<DataFetchingEnvironment> environment;
    private final ExecutionStrategyParameters executionStrategyParameters;
    private final boolean trivialDataFetcher;

    public InstrumentationFieldFetchParameters(ExecutionContext getExecutionContext, Supplier<DataFetchingEnvironment> environment, ExecutionStrategyParameters executionStrategyParameters, boolean trivialDataFetcher) {
        super(getExecutionContext, () -> environment.get().getExecutionStepInfo());
        this.environment = environment;
        this.executionStrategyParameters = executionStrategyParameters;
        this.trivialDataFetcher = trivialDataFetcher;
    }

    public DataFetchingEnvironment getEnvironment() {
        return environment.get();
    }

    public boolean isTrivialDataFetcher() {
        return trivialDataFetcher;
    }
}
