package graphql.execution.incremental;

import graphql.ExperimentalApi;
import graphql.normalized.incremental.NormalizedDeferredExecution;
import org.jetbrains.annotations.Nullable;

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
}
