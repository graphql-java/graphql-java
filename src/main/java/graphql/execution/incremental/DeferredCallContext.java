package graphql.execution.incremental;

import graphql.ExceptionWhileDataFetching;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.execution.ExecutionStrategyParameters;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Contains data relevant to the execution of a {@link DeferredCall}.
 * <p>
 * The responsibilities of this class are similar to {@link graphql.execution.ExecutionContext}, but restricted to the
 * execution of a deferred call (instead of the whole GraphQL execution like {@link graphql.execution.ExecutionContext}).
 * <p>
 * Some behaviours, like error capturing, need to be scoped to a single {@link DeferredCall}, because each defer payload
 * contains its own distinct list of errors.
 */
@Internal
public class DeferredCallContext {

    private final List<GraphQLError> errors = new CopyOnWriteArrayList<>();

    public void onFetchingException(ExecutionStrategyParameters parameters, Throwable e) {
        ExceptionWhileDataFetching error = new ExceptionWhileDataFetching(parameters.getPath(), e, parameters.getField().getSingleField().getSourceLocation());
        onError(error);
    }

    public void onError(GraphQLError graphqlError) {
        errors.add(graphqlError);
    }

    /**
     * @return a list of errors that were encountered while executing this deferred call
     */
    public List<GraphQLError> getErrors() {
        return errors;
    }
}
