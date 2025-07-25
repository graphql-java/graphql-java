package graphql.execution.incremental;

import graphql.ExperimentalApi;
import graphql.normalized.incremental.NormalizedDeferredExecution;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Represents details about the defer execution that can be associated with a {@link graphql.execution.MergedField}.
 * <p>
 * This representation is used during graphql execution. Check {@link NormalizedDeferredExecution}
 * for the normalized representation of @defer.
 */
@ExperimentalApi
public class DeferredExecution {
    private final String label;

    public DeferredExecution(String label) {
        this.label = label;
    }

    @Nullable
    public String getLabel() {
        return label;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DeferredExecution that = (DeferredExecution) o;
        return Objects.equals(label, that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(label);
    }
}
