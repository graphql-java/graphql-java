package graphql.execution;

import graphql.Internal;
import graphql.collect.ImmutableMapWithNullValues;

import java.util.Map;

/**
 * Holds coerced variables
 */
@Internal
public class CoercedVariables {
    private final ImmutableMapWithNullValues<String, Object> coercedVariables;

    public CoercedVariables(Map<String, Object> coercedVariables) {
        this.coercedVariables = ImmutableMapWithNullValues.copyOf(coercedVariables);
    }

    public Map<String, Object> getCoercedVariables() {
        return coercedVariables;
    }

    public boolean containsKey(String key) {
        return coercedVariables.containsKey(key);
    }

    public Object get(String key) {
        return coercedVariables.get(key);
    }
}
