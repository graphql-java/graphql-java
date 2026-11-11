package graphql.execution;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;

import java.util.function.Consumer;

/**
 * Limits the nesting of external input values during coercion. Each list or input object value
 * on a path increases the depth by one. Sibling values do not add to each other's depth,
 * Non-Null wrappers do not count, and scalar values are not traversed. A value coerced to a
 * singleton list counts as one list level.
 *
 * <p>Put an instance under {@link #KEY} in {@code ExecutionInput.getGraphQLContext()} to
 * override the JVM-wide default of 128 for a request. Use {@link #NONE} to disable the limit.
 * <pre>{@code
 * InputCoercionLimits limits = InputCoercionLimits.newInputCoercionLimits()
 *         .maxDepth(64)
 *         .build();
 * executionInput.getGraphQLContext().put(InputCoercionLimits.KEY, limits);
 * }</pre>
 */
@PublicApi
@NullMarked
public class InputCoercionLimits {

    public static final int DEFAULT_MAX_DEPTH = 128;
    public static final String KEY = "graphql.execution.InputCoercionLimits";
    public static final InputCoercionLimits DEFAULT = new InputCoercionLimits(DEFAULT_MAX_DEPTH);
    public static final InputCoercionLimits NONE = new InputCoercionLimits(Integer.MAX_VALUE);

    private static volatile InputCoercionLimits defaultLimits = DEFAULT;

    private final int maxDepth;

    private InputCoercionLimits(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    /**
     * Sets the JVM-wide default used when a request has no override.
     */
    public static void setDefaultLimits(InputCoercionLimits limits) {
        defaultLimits = limits;
    }

    /**
     * Returns the JVM-wide default.
     */
    public static InputCoercionLimits getDefaultLimits() {
        return defaultLimits;
    }

    /**
     * Creates a builder initialized from the current JVM-wide default.
     */
    public static Builder newInputCoercionLimits() {
        return new Builder(getDefaultLimits());
    }

    /**
     * Returns the maximum number of nested list and input object values on one path.
     */
    public int getMaxDepth() {
        return maxDepth;
    }

    /**
     * Creates a copy with the changes made by the builder consumer.
     */
    public InputCoercionLimits transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @PublicApi
    @NullUnmarked
    public static class Builder {
        private int maxDepth;

        private Builder(InputCoercionLimits limits) {
            this.maxDepth = limits.maxDepth;
        }

        /**
         * Sets the maximum depth. The value must be positive.
         */
        public Builder maxDepth(int maxDepth) {
            if (maxDepth <= 0) {
                throw new IllegalArgumentException("maxDepth must be positive");
            }
            this.maxDepth = maxDepth;
            return this;
        }

        /**
         * Creates the limits instance.
         */
        public InputCoercionLimits build() {
            return new InputCoercionLimits(maxDepth);
        }
    }
}
