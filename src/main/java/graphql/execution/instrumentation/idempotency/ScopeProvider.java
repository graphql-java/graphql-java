package graphql.execution.instrumentation.idempotency;

import graphql.execution.ExecutionContext;

/**
 * <p>
 * ScopeProvider is an extension interface for extracting the scope (e.g. user) from a mutation
 * execution using {@link ExecutionContext}.
 * </p>
 *
 * <p>
 * The default {@link InputContextScopeProvider} simply uses the return value of
 * {@link ExecutionContext#getContext()} for this purpose.
 * Custom implementations may choose to make use of specific context objects, access
 * authentication context holders, web server requests, or whatever is appropriate for the
 * specific use case.
 * </p>
 *
 * <p>
 * Implementations must be thread-safe. They may return null scopes, making this specific mutation
 * belong to a generic default scope shared with all other unscoped mutations.
 * </p>
 *
 * @author <a href="mailto:mario@ellebrecht.com">Mario Ellebrecht &lt;mario@ellebrecht.com&gt;</a>
 */
@FunctionalInterface
public interface ScopeProvider {

  Object getScope(ExecutionContext context);

}
