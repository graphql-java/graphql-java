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

    private InstrumentationFieldFetchParameters(ExecutionContext getExecutionContext, Supplier<DataFetchingEnvironment> environment, InstrumentationState instrumentationState, ExecutionStrategyParameters executionStrategyParameters, boolean trivialDataFetcher) {
        super(getExecutionContext, () -> environment.get().getExecutionStepInfo(), instrumentationState);
        this.environment = environment;
        this.executionStrategyParameters = executionStrategyParameters;
        this.trivialDataFetcher = trivialDataFetcher;
    }

    /**
     * Returns a cloned parameters object with the new state
     *
     * @param instrumentationState the new state for this parameters object
     *
     * @return a new parameters object with the new state
     *
     * @deprecated state is now passed in direct to instrumentation methods
     */
    @Deprecated(since = "2022-07-26")
    @Override
    public InstrumentationFieldFetchParameters withNewState(InstrumentationState instrumentationState) {
        return new InstrumentationFieldFetchParameters(
                this.getExecutionContext(), this.environment,
                instrumentationState, executionStrategyParameters, trivialDataFetcher);
    }


    public DataFetchingEnvironment getEnvironment() {
        return environment.get();
    }

    public boolean isTrivialDataFetcher() {
        return trivialDataFetcher;
    }
}
