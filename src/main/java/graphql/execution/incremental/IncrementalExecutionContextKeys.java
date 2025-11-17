package graphql.execution.incremental;


import graphql.Internal;
import org.jspecify.annotations.NullMarked;

/**
 * GraphQLContext keys for controlling incremental execution behavior.
 */
@Internal
@NullMarked
public final class IncrementalExecutionContextKeys {
    private IncrementalExecutionContextKeys() {
    }

    /**
     * Enables eager start of @defer processing so defered work runs before the initial result is computed. 
     * Defaults to false.
     * <p>
     * Expects a boolean value.
     */
    public static final String ENABLE_EAGER_DEFER_START = "__GJ_enable_eager_defer_start";

}


