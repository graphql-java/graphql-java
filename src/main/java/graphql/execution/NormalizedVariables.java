package graphql.execution;

import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.collect.ImmutableMapWithNullValues;
import graphql.normalized.NormalizedInputValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Holds coerced variables, that is their values are now in a normalized {@link graphql.normalized.NormalizedInputValue} form.
 */
@PublicApi
@NullMarked
public class NormalizedVariables {
    private final ImmutableMapWithNullValues<String, NormalizedInputValue> normalisedVariables;

    public NormalizedVariables(Map<String, NormalizedInputValue> normalisedVariables) {
        this.normalisedVariables = ImmutableMapWithNullValues.copyOf(normalisedVariables);
    }

    public Map<String, NormalizedInputValue> toMap() {
        return normalisedVariables;
    }

    public boolean containsKey(String key) {
        return normalisedVariables.containsKey(key);
    }

    public @Nullable Object get(String key) {
        return normalisedVariables.get(key);
    }

    public static NormalizedVariables emptyVariables() {
        return new NormalizedVariables(ImmutableKit.emptyMap());
    }

    public static NormalizedVariables of(Map<String, NormalizedInputValue> normalisedVariables) {
        return new NormalizedVariables(normalisedVariables);
    }

    @Override
    public String toString() {
        return normalisedVariables.toString();
    }
}
