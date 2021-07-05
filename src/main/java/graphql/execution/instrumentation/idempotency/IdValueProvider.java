package graphql.execution.instrumentation.idempotency;

import graphql.execution.FetchedValue;

/**
 * {@link ValueProvider} implementation that extracts an <code>id</code> property, method or field
 * value using reflection.
 *
 * @author <a href="mailto:mario@ellebrecht.com">Mario Ellebrecht &lt;mario@ellebrecht.com&gt;</a>
 */
public class IdValueProvider implements ValueProvider {

  private static final String ID_PROPERTY_GETTER = "getId";
  private static final String ID_FIELD_OR_METHOD = "id";

  @Override
  public Object getValue(Object fetchedValue) {
    if (fetchedValue == null) {
      return null;
    }
    final Object value =
        fetchedValue instanceof FetchedValue
            ? ((FetchedValue) fetchedValue).getFetchedValue()
            : fetchedValue;
    if (value == null) {
      return null;
    }
    final Class<?> c = value.getClass();
    if (c == String.class || c == Object.class || c.isPrimitive()) {
      return value.toString();
    }
    Object id;
    try {
      id = c.getMethod(ID_PROPERTY_GETTER).invoke(value);
      if (id != null) {
        return id.toString();
      }
    } catch (Exception e) {
      // nothing to do here
    }
    try {
      id = c.getMethod(ID_FIELD_OR_METHOD).invoke(value);
      if (id != null) {
        return id.toString();
      }
    } catch (Exception e) {
      // nothing to do here
    }
    try {
      id = c.getField(ID_FIELD_OR_METHOD).get(value);
      if (id != null) {
        return id.toString();
      }
    } catch (Exception e) {
      // nothing to do here
    }
    return null;
  }

}
