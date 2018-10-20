package graphql.analysis;

import graphql.PublicApi;
import graphql.execution.AbortExecutionException;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.validation.ValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static graphql.execution.instrumentation.SimpleInstrumentationContext.whenCompleted;

/**
 * Prevents execution if the query depth is greater than the specified maxDepth
 */
@PublicApi
public class MaxQueryDepthInstrumentation extends SimpleInstrumentation {

    private static final Logger log = LoggerFactory.getLogger(MaxQueryDepthInstrumentation.class);

    private final int maxDepth;
    private final boolean abortExecution;

    /**
     * new Instrumentation
     *
     * @param maxDepth max allowed depth, otherwise execution will be aborted
     */
    public MaxQueryDepthInstrumentation(int maxDepth) {
        this(maxDepth, true);
    }

    /**
     * new Instrumentation
     *
     * @param maxDepth       max allowed depth, otherwise execution will be aborted
     * @param abortExecution whether to abort execution or only log a warning
     */
    public MaxQueryDepthInstrumentation(int maxDepth, boolean abortExecution) {
        this.maxDepth = maxDepth;
        this.abortExecution = abortExecution;
    }

    @Override
    public InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
        return whenCompleted((errors, throwable) -> {
            if ((errors != null && errors.size() > 0) || throwable != null) {
                return;
            }
            QueryTraversal queryTraversal = newQueryTraversal(parameters);
            int depth = queryTraversal.reducePreOrder((env, acc) -> Math.max(getPathLength(env.getParentEnvironment()), acc), 0);
            log.debug("Query depth: {}", depth);
            if (depth > maxDepth) {
                if (abortExecution) {
                    throw mkAbortException(depth, maxDepth);
                } else {
                    log.warn("Maximum query depth exceeded {} > {}", depth, maxDepth);
                }
            }
        });
    }

    /**
     * Called to generate your own error message or custom exception class
     *
     * @param depth    the depth of the query
     * @param maxDepth the maximum depth allowed
     *
     * @return a instance of AbortExecutionException
     */
    protected AbortExecutionException mkAbortException(int depth, int maxDepth) {
        return new AbortExecutionException("maximum query depth exceeded " + depth + " > " + maxDepth);
    }

    QueryTraversal newQueryTraversal(InstrumentationValidationParameters parameters) {
        return QueryTraversal.newQueryTraversal()
                .schema(parameters.getSchema())
                .document(parameters.getDocument())
                .operationName(parameters.getOperation())
                .variables(parameters.getVariables())
                .build();
    }

    private int getPathLength(QueryVisitorFieldEnvironment path) {
        int length = 1;
        while (path != null) {
            path = path.getParentEnvironment();
            length++;
        }
        return length;
    }
}
