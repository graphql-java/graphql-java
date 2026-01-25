package graphql.execution;

import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.collect.ImmutableMapWithNullValues;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Holds coerced variables, that is their values are now in a canonical form.
 */
@PublicApi
@NullMarked
public class CoercedVariables {
    private static final CoercedVariables EMPTY = CoercedVariables.of(ImmutableKit.emptyMap());
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

    public @Nullable Object get(String key) {
        return coercedVariables.get(key);
    }

    public static CoercedVariables emptyVariables() {
        return EMPTY;
    }

    public static CoercedVariables of(Map<String, Object> coercedVariables) {
        return new CoercedVariables(coercedVariables);
    }

    @Override
    public String toString() {
        return coercedVariables.toString();
    }
}
