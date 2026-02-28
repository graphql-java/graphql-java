package graphql.analysis;

import graphql.ExecutionResult;
import graphql.PublicApi;
import graphql.execution.AbortExecutionException;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

import static graphql.execution.instrumentation.SimpleInstrumentationContext.noOp;

/**
 * Prevents execution if the query depth is greater than the specified maxDepth.
 * <p>
 * Use the {@code Function<QueryDepthInfo, Boolean>} parameter to supply a function to perform a custom action when the max depth is
 * exceeded. If the function returns {@code true} a {@link AbortExecutionException} is thrown.
 */
@PublicApi
@NullMarked
public class MaxQueryDepthInstrumentation extends SimplePerformantInstrumentation {

    private final int maxDepth;
    private final Function<QueryDepthInfo, Boolean> maxQueryDepthExceededFunction;

    /**
     * Creates a new instrumentation that tracks the query depth.
     *
     * @param maxDepth max allowed depth, otherwise execution will be aborted
     */
    public MaxQueryDepthInstrumentation(int maxDepth) {
        this(maxDepth, (queryDepthInfo) -> true);
    }

    /**
     * Creates a new instrumentation that tracks the query depth.
     *
     * @param maxDepth                      max allowed depth, otherwise execution will be aborted
     * @param maxQueryDepthExceededFunction the function to perform when the max depth is exceeded
     */
    public MaxQueryDepthInstrumentation(int maxDepth, Function<QueryDepthInfo, Boolean> maxQueryDepthExceededFunction) {
        this.maxDepth = maxDepth;
        this.maxQueryDepthExceededFunction = maxQueryDepthExceededFunction;
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
        QueryTraverser queryTraverser = newQueryTraverser(parameters.getExecutionContext());
        int depth = queryTraverser.reducePreOrder((env, acc) -> Math.max(getPathLength(env.getParentEnvironment()), acc), 0);
        if (depth > maxDepth) {
            QueryDepthInfo queryDepthInfo = QueryDepthInfo.newQueryDepthInfo()
                    .depth(depth)
                    .build();
            boolean throwAbortException = maxQueryDepthExceededFunction.apply(queryDepthInfo);
            if (throwAbortException) {
                throw mkAbortException(depth, maxDepth);
            }
        }
        return noOp();
    }

    /**
     * Called to generate your own error message or custom exception class
     *
     * @param depth    the depth of the query
     * @param maxDepth the maximum depth allowed
     *
     * @return an instance of AbortExecutionException
     */
    protected AbortExecutionException mkAbortException(int depth, int maxDepth) {
        return new AbortExecutionException("maximum query depth exceeded " + depth + " > " + maxDepth);
    }

    QueryTraverser newQueryTraverser(ExecutionContext executionContext) {
        return QueryTraverser.newQueryTraverser()
                .schema(executionContext.getGraphQLSchema())
                .document(executionContext.getDocument())
                .operationName(executionContext.getExecutionInput().getOperationName())
                .coercedVariables(executionContext.getCoercedVariables())
                .build();
    }

    private int getPathLength(@Nullable QueryVisitorFieldEnvironment path) {
        int length = 1;
        while (path != null) {
            path = path.getParentEnvironment();
            length++;
        }
        return length;
    }
}
