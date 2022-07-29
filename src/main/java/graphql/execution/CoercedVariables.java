package graphql.execution;

import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.collect.ImmutableMapWithNullValues;

import java.util.Map;

/**
 * Holds coerced variables, that is their values are now in a canonical form.
 */
@PublicApi
public class CoercedVariables {
    private final ImmutableMapWithNullValues<String, Object> coercedVariables;

    public CoercedVariables(Map<String, Object> coercedVariables) {
        this.coercedVariables = ImmutableMapWithNullValues.copyOf(coercedVariables);
    }

    public Map<String, Object> toMap() {
        return coercedVariables;
    }

    public boolean containsKey(String key) {
        return coercedVariables.containsKey(key);
    }

    public Object get(String key) {
        return coercedVariables.get(key);
    }

    public static CoercedVariables emptyVariables() {
        return new CoercedVariables(ImmutableKit.emptyMap());
    }

    public static CoercedVariables of(Map<String, Object> coercedVariables) {
        return new CoercedVariables(coercedVariables);
    }
}
