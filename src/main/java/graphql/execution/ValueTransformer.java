package graphql.execution;

/**
 * Overrideable class that allows transformation of values pre-coercion
 *
 */
public abstract class ValueTransformer {

  public abstract Object transformValue(Object value);
}
