package graphql.execution.instrumentation.idempotency;

import graphql.execution.AbortExecutionException;
import java.util.HashMap;
import java.util.Map;

/**
 * Exception thrown when an idempotency key is encountered more than once in the same scope.
 * Besides the key it will also contain the previous mutation result value as extracted by the
 * configured {@link ValueProvider}.
 *
 * @author <a href="mailto:mario@ellebrecht.com">Mario Ellebrecht &lt;mario@ellebrecht.com&gt;</a>
 */
public final class IdempotencyException extends AbortExecutionException {

  private static final long serialVersionUID = -7077119608480767116L;

  private final String key;
  private final Object value;

  public IdempotencyException(String key, Object value) {
    super("Mutation with idempotency key " + key + " was already processed");
    this.key = key;
    this.value = value;
  }

  public String getKey() {
    return key;
  }

  public Object getValue() {
    return value;
  }

  public Map<String, Object> getData() {
    final Map<String, Object> data = new HashMap<>();
    data.put("key", key);
    data.put("value", value);
    return data;
  }

}
