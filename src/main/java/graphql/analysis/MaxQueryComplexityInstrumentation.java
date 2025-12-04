package graphql.analysis;

import graphql.ExecutionResult;
import graphql.PublicApi;
import graphql.execution.AbortExecutionException;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.validation.ValidationError;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static graphql.Assert.assertNotNull;
import static graphql.execution.instrumentation.InstrumentationState.ofState;
import static graphql.execution.instrumentation.SimpleInstrumentationContext.noOp;

/**
 * Prevents execution if the query complexity is greater than the specified maxComplexity.
 * <p>
 * Use the {@code Function<QueryComplexityInfo, Boolean>} parameter to supply a function to perform a custom action when the max complexity
 * is exceeded. If the function returns {@code true} a {@link AbortExecutionException} is thrown.
 */
@PublicApi
@NullMarked
public class MaxQueryComplexityInstrumentation extends SimplePerformantInstrumentation {

    private final int maxComplexity;
    private final FieldComplexityCalculator fieldComplexityCalculator;
    private final Function<QueryComplexityInfo, Boolean> maxQueryComplexityExceededFunction;

    /**
     * new Instrumentation with default complexity calculator which is `1 + childComplexity`
     *
     * @param maxComplexity max allowed complexity, otherwise execution will be aborted
     */
    public MaxQueryComplexityInstrumentation(int maxComplexity) {
        this(maxComplexity, (queryComplexityInfo) -> true);
    }

    /**
     * new Instrumentation with default complexity calculator which is `1 + childComplexity`
     *
     * @param maxComplexity                      max allowed complexity, otherwise execution will be aborted
     * @param maxQueryComplexityExceededFunction the function to perform when the max complexity is exceeded
     */
    public MaxQueryComplexityInstrumentation(int maxComplexity, Function<QueryComplexityInfo, Boolean> maxQueryComplexityExceededFunction) {
        this(maxComplexity, (env, childComplexity) -> 1 + childComplexity, maxQueryComplexityExceededFunction);
    }

    /**
     * new Instrumentation with custom complexity calculator
     *
     * @param maxComplexity             max allowed complexity, otherwise execution will be aborted
     * @param fieldComplexityCalculator custom complexity calculator
     */
    public MaxQueryComplexityInstrumentation(int maxComplexity, FieldComplexityCalculator fieldComplexityCalculator) {
        this(maxComplexity, fieldComplexityCalculator, (queryComplexityInfo) -> true);
    }

    /**
     * new Instrumentation with custom complexity calculator
     *
     * @param maxComplexity                      max allowed complexity, otherwise execution will be aborted
     * @param fieldComplexityCalculator          custom complexity calculator
     * @param maxQueryComplexityExceededFunction the function to perform when the max complexity is exceeded
     */
    public MaxQueryComplexityInstrumentation(int maxComplexity, FieldComplexityCalculator fieldComplexityCalculator,
                                             Function<QueryComplexityInfo, Boolean> maxQueryComplexityExceededFunction) {
        this.maxComplexity = maxComplexity;
        this.fieldComplexityCalculator = assertNotNull(fieldComplexityCalculator, "calculator can't be null");
        this.maxQueryComplexityExceededFunction = maxQueryComplexityExceededFunction;
    }

    @Override
    public CompletableFuture<InstrumentationState> createStateAsync(InstrumentationCreateStateParameters parameters) {
        return CompletableFuture.completedFuture(new State());
    }

    @Override
    public InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters, InstrumentationState rawState) {
        State state = ofState(rawState);
        // for API backwards compatibility reasons we capture the validation parameters, so we can put them into QueryComplexityInfo
        state.instrumentationValidationParameters.set(parameters);
        return noOp();
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters instrumentationExecuteOperationParameters, InstrumentationState rawState) {
        State state = ofState(rawState);
        QueryComplexityCalculator queryComplexityCalculator = newQueryComplexityCalculator(instrumentationExecuteOperationParameters.getExecutionContext());
        int totalComplexity = queryComplexityCalculator.calculate();
        if (totalComplexity > maxComplexity) {
            QueryComplexityInfo queryComplexityInfo = QueryComplexityInfo.newQueryComplexityInfo()
                    .complexity(totalComplexity)
                    .instrumentationValidationParameters(state.instrumentationValidationParameters.get())
                    .instrumentationExecuteOperationParameters(instrumentationExecuteOperationParameters)
                    .build();
            boolean throwAbortException = maxQueryComplexityExceededFunction.apply(queryComplexityInfo);
            if (throwAbortException) {
                throw mkAbortException(totalComplexity, maxComplexity);
            }
        }
        return noOp();
    }

    private QueryComplexityCalculator newQueryComplexityCalculator(ExecutionContext executionContext) {
        return QueryComplexityCalculator.newCalculator()
                .fieldComplexityCalculator(fieldComplexityCalculator)
                .schema(executionContext.getGraphQLSchema())
                .document(executionContext.getDocument())
                .operationName(executionContext.getExecutionInput().getOperationName())
                .variables(executionContext.getCoercedVariables())
                .build();
    }

    /**
     * Called to generate your own error message or custom exception class
     *
     * @param totalComplexity the complexity of the query
     * @param maxComplexity   the maximum complexity allowed
     *
     * @return an instance of AbortExecutionException
     */
    protected AbortExecutionException mkAbortException(int totalComplexity, int maxComplexity) {
        return new AbortExecutionException("maximum query complexity exceeded " + totalComplexity + " > " + maxComplexity);
    }

    private static class State implements InstrumentationState {
        AtomicReference<InstrumentationValidationParameters> instrumentationValidationParameters = new AtomicReference<>();
    }

}
