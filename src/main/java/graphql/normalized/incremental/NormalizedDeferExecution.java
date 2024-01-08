package graphql.normalized.incremental;

import graphql.ExperimentalApi;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Represents the aspects of defer that are important for runtime execution.
 */
@ExperimentalApi
public class NormalizedDeferExecution {
    private final DeferBlock deferBlock;
    private final Set<String> objectTypeNames;

    public NormalizedDeferExecution(DeferBlock deferBlock, Set<String> objectTypeNames) {
        this.deferBlock = deferBlock;
        this.objectTypeNames = objectTypeNames;
    }

    public Set<String> getObjectTypeNames() {
        return objectTypeNames;
    }

    public DeferBlock getDeferBlock() {
        return deferBlock;
    }

    // TODO: Javadoc
    @ExperimentalApi
    public static class DeferBlock {
        private final String label;

        public DeferBlock(@Nullable String label) {
            this.label = label;
        }

        @Nullable
        public String getLabel() {
            return label;
        }
    }
}
