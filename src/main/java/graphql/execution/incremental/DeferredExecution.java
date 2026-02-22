package graphql.execution.incremental;

import graphql.ExperimentalApi;
import graphql.normalized.incremental.NormalizedDeferredExecution;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Represents details about the defer execution that can be associated with a {@link graphql.execution.MergedField}.
 * <p>
 * This representation is used during graphql execution. Check {@link NormalizedDeferredExecution}
 * for the normalized representation of @defer.
 */
@ExperimentalApi
@NullMarked
public class DeferredExecution {
    private final String label;

    public DeferredExecution(String label) {
        this.label = label;
    }

    @Nullable
    public String getLabel() {
        return label;
    }

    // this class uses object identity - do not put .equals() / .hashCode() implementations on it
    // otherwise it will break defer handling.  I have put the code just to be explicit that object identity
    // is needed

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }
}
