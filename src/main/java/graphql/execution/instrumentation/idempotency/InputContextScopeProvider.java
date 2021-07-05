package graphql.execution.instrumentation.idempotency;

import graphql.execution.ExecutionContext;

/**
 * Simple {@link ScopeProvider} implementation that uses the embedded context object from {@link
 * ExecutionContext#getContext()}.
 *
 * @author <a href="mailto:mario@ellebrecht.com">Mario Ellebrecht &lt;mario@ellebrecht.com&gt;</a>
 */
public class InputContextScopeProvider implements ScopeProvider {

  @Override
  public Object getScope(ExecutionContext context) {
    return context == null ? null : context.getContext();
  }

}
