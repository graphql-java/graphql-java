package graphql.execution;

import graphql.Internal;
import graphql.collect.ImmutableMapWithNullValues;

import java.util.Collections;
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

    public Map<String, Object> toMap() {
        return rawVariables;
    }

    public boolean containsKey(String key) {
        return rawVariables.containsKey(key);
    }

    public Object get(String key) {
        return rawVariables.get(key);
    }

    public static RawVariables emptyVariables() {
        return new RawVariables(Collections.emptyMap());
    }
}
