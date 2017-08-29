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

    private int maxDepth;

    public MaxQueryDepthInstrumentation(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    @Override
    public InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
        return (result, throwable) -> {
            QueryTraversal queryTraversal = new QueryTraversal(
                    parameters.getSchema(),
                    parameters.getDocument(),
                    parameters.getOperation(),
                    parameters.getVariables()
            );
            int depth = queryTraversal.reducePreOrder((env, acc) -> Math.max(getPathLength(env.getPath()), acc), 0);
            if (depth > maxDepth) {
                throw new AbortExecutionException("maximum query depth exceeded " + depth + " > " + maxDepth);
            }
        };
    }

    private int getPathLength(QueryPath path) {
        int length = 1;
        while (path != null) {
            path = path.getParentPath();
            length++;
        }
        return length;
    }
}
