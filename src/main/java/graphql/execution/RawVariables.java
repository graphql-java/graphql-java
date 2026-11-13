package graphql.execution;

import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.collect.ImmutableMapWithNullValues;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Holds raw variables, which have not been coerced yet into {@link CoercedVariables}
 */
@PublicApi
@NullMarked
public class RawVariables {
    private static final RawVariables EMPTY = RawVariables.of(ImmutableKit.emptyMap());
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

    public @Nullable Object get(String key) {
        return rawVariables.get(key);
    }

    public static RawVariables emptyVariables() {
        return EMPTY;
    }

    public static RawVariables of(Map<String, Object> rawVariables) {
        return new RawVariables(rawVariables);
    }

    @Override
    public String toString() {
        return rawVariables.toString();
    }
}
