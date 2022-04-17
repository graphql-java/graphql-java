package graphql.execution;

import graphql.Internal;
import graphql.collect.ImmutableMapWithNullValues;

import java.util.Map;

/**
 * Holds raw variables, which not have been coerced yet
 */
@Internal
public class RawVariables {
    private final ImmutableMapWithNullValues<String, Object> rawVariables;

    public RawVariables(Map<String, Object> rawVariables) {
        this.rawVariables = ImmutableMapWithNullValues.copyOf(rawVariables);
    }

    public Map<String, Object> getRawVariables() {
        return rawVariables;
    }

    public boolean containsKey(String key) {
        return rawVariables.containsKey(key);
    }

    public Object get(String key) {
        return rawVariables.get(key);
    }
}
