package graphql.execution.incremental;


import graphql.GraphQLContext;
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


    /**
     * Stores the Publisher<DelayedIncrementalPartialResult> used for incremental delivery when eager defer is enabled.
     * Value type: org.reactivestreams.Publisher
     */
    public static final String EAGER_DEFER_PUBLISHER = "__GJ_eager_defer_publisher";
}


