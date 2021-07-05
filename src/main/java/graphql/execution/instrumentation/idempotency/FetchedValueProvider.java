package graphql.execution.instrumentation.idempotency;

import graphql.execution.FetchedValue;

/**
 * Simple {@link ValueProvider} implementation that uses the fetched value as-is.
 *
 * @author <a href="mailto:mario@ellebrecht.com">Mario Ellebrecht &lt;mario@ellebrecht.com&gt;</a>
 */
public class FetchedValueProvider implements ValueProvider {

  @Override
  public Object getValue(Object fetchedValue) {
    if (fetchedValue instanceof FetchedValue) {
      return ((FetchedValue) fetchedValue).getFetchedValue();
    } else {
      return fetchedValue;
    }
  }

}
