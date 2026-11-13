package graphql.execution.instrumentation.parameters;

import graphql.PublicApi;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
@NullMarked
@PublicApi
public class InstrumentationFieldFetchParameters extends InstrumentationFieldParameters {
    private final Supplier<DataFetchingEnvironment> environment;
    private final ExecutionStrategyParameters executionStrategyParameters;
    private final boolean trivialDataFetcher;

    public InstrumentationFieldFetchParameters(ExecutionContext executionContext, Supplier<DataFetchingEnvironment> environment, ExecutionStrategyParameters executionStrategyParameters, boolean trivialDataFetcher) {
        this(executionContext, null, environment, executionStrategyParameters, trivialDataFetcher);
    }

    /**
     * @param executionContext            the execution context
     * @param fieldDefinition             the field definition if it is already known, which allows {@link #getField()} to answer
     *                                    without building the {@link DataFetchingEnvironment}, or null if it is not known
     * @param environment                 a supplier of the {@link DataFetchingEnvironment} for this field fetch
     * @param executionStrategyParameters the execution strategy parameters
     * @param trivialDataFetcher          whether the data fetcher is a {@link graphql.TrivialDataFetcher}
     */
    public InstrumentationFieldFetchParameters(ExecutionContext executionContext, @Nullable GraphQLFieldDefinition fieldDefinition, Supplier<DataFetchingEnvironment> environment, ExecutionStrategyParameters executionStrategyParameters, boolean trivialDataFetcher) {
        super(executionContext, fieldDefinition, () -> environment.get().getExecutionStepInfo());
        this.environment = environment;
        this.executionStrategyParameters = executionStrategyParameters;
        this.trivialDataFetcher = trivialDataFetcher;
    }

    /**
     * This returns the {@link DataFetchingEnvironment} for this field fetch.  Note that the environment is built lazily
     * and calling this will materialise it, which has a cost.  The same is true of {@link #getExecutionStepInfo()}.
     * If you only need the field definition (for example to decide whether the field type is a leaf type) then
     * use {@link #getField()} instead, which the engine supplies eagerly without building the environment.
     *
     * @return the data fetching environment for this field fetch
     */
    public DataFetchingEnvironment getEnvironment() {
        return environment.get();
    }

    public boolean isTrivialDataFetcher() {
        return trivialDataFetcher;
    }
}
