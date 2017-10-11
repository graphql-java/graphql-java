package graphql.analysis;

import graphql.PublicApi;
import graphql.execution.AbortExecutionException;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.NoOpInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.validation.ValidationError;

import java.util.List;

/**
 * Prevents execution if the query depth is greater than the specified maxDepth
 */
@PublicApi
public class MaxQueryDepthInstrumentation extends NoOpInstrumentation {

    private final int maxDepth;

    public MaxQueryDepthInstrumentation(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    @Override
    public InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
        return (errors, throwable) -> {
            if ((errors != null && errors.size() > 0) || throwable != null) {
                return;
            }
            QueryTraversal queryTraversal = newQueryTraversal(parameters);
            int depth = queryTraversal.reducePreOrder((env, acc) -> Math.max(getPathLength(env.getParentEnvironment()), acc), 0);
            if (depth > maxDepth) {
                throw new AbortExecutionException("maximum query depth exceeded " + depth + " > " + maxDepth);
            }
        };
    }

    QueryTraversal newQueryTraversal(InstrumentationValidationParameters parameters) {
        return new QueryTraversal(
                parameters.getSchema(),
                parameters.getDocument(),
                parameters.getOperation(),
                parameters.getVariables()
        );
    }

    private int getPathLength(QueryVisitorEnvironment path) {
        int length = 1;
        while (path != null) {
            path = path.getParentEnvironment();
            length++;
        }
        return length;
    }
}
