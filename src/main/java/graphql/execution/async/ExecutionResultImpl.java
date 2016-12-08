package graphql.execution.async;

import graphql.ExecutionResult;
import graphql.GraphQLError;

import java.util.List;

/**
 * `graphql.ExecutionResultImpl` shallow copies the list of passed-in errors before storing it,
 * which prevents sharing the list of errors among threads. This implementation stores and encapsulates
 * the errors list reference directly so that it may be shared among threads.
 */
class ExecutionResultImpl implements ExecutionResult {

    private final Object data;
    private final List<GraphQLError> errors;

    ExecutionResultImpl(Object data, List<GraphQLError> errors) {
        this.data = data;
        this.errors = errors;
    }

    @Override
    public Object getData() {
        return data;
    }

    @Override
    public List<GraphQLError> getErrors() {
        return errors;
    }
}
