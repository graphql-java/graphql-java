package graphql.execution.lazy;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.execution.ExecutionContext;
import graphql.execution.defer.DeferSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An execution result used for executions that contain lazy objects. The major differences between this and
 * {@link graphql.ExecutionResultImpl} are
 *
 * <ul>
 * <li>{@link LazyExecutionResult#getErrors()} always returns the current errors from the execution context</li>
 * <li>{@link LazyExecutionResult#getExtensions()} always returns all currently used extensions</li>
 * <li>{@link LazyExecutionResult#toSpecification()} is not supported by this implementation</li>
 * </ul>
 */
@Internal
public class LazyExecutionResult implements ExecutionResult {
    private final ExecutionResult delegate;
    private final List<GraphQLError> contextErrors;
    private final DeferSupport deferSupport;

    public LazyExecutionResult(ExecutionResult delegate, List<GraphQLError> contextErrors, DeferSupport deferSupport) {
        this.delegate = delegate;
        this.contextErrors = contextErrors;
        this.deferSupport = deferSupport;
    }

    @Override
    public <T> T getData() {
        return delegate.getData();
    }

    @Override
    public List<GraphQLError> getErrors() {
        return new ArrayList<>(contextErrors);
    }

    @Override
    public Map<Object, Object> getExtensions() {
        Map<Object, Object> extensions = delegate.getExtensions();
        if (extensions == null) {
            extensions = new LinkedHashMap<>();
        }
        if (deferSupport != null && deferSupport.isDeferDetected()) {
            extensions.put(GraphQL.DEFERRED_RESULTS, deferSupport.getPublisher());
        }
        return extensions.isEmpty() ? null : extensions;
    }

    @Override
    public Map<String, Object> toSpecification() {
        throw new UnsupportedOperationException();
    }
}
