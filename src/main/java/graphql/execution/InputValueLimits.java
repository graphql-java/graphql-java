package graphql.execution;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;

import java.util.function.Consumer;

/**
 * Limits the nesting of supplied variable values and schema default values. Each list or input object value
 * on a path increases the depth by one. Sibling values do not add to each other's depth,
 * Non-Null wrappers do not count, and scalar values are not traversed. A value coerced to a
 * singleton list counts as one list level.
 *
 * <p>Schema defaults, whether SDL literals or programmatic values, are checked independently
 * when the schema is validated, using the current JVM-wide limit. Defaults are not checked
 * again against request overrides when they are used or exposed through introspection.
 * Scalar payloads and defaults filled in for omitted fields do not add to the supplied value's depth.
 * Programmatic list defaults must use collections or arrays; iterators, streams and other iterables
 * are rejected during schema validation without being consumed.
 *
 * <p>Put an instance under {@link #KEY} in {@code ExecutionInput.getGraphQLContext()} to
 * override the JVM-wide default of 128 for supplied variable values in a request.
 * Use {@link #NONE} to disable the limit.
 * <pre>{@code
 * InputValueLimits limits = InputValueLimits.newInputValueLimits()
 *         .maxDepth(64)
 *         .build();
 * executionInput.getGraphQLContext().put(InputValueLimits.KEY, limits);
 * }</pre>
 */
@PublicApi
@NullMarked
public class InputValueLimits {

    public static final int DEFAULT_MAX_DEPTH = 128;
    public static final String KEY = "graphql.execution.InputValueLimits";
    public static final InputValueLimits DEFAULT = new InputValueLimits(DEFAULT_MAX_DEPTH);
    public static final InputValueLimits NONE = new InputValueLimits(Integer.MAX_VALUE);

    private static volatile InputValueLimits defaultLimits = DEFAULT;

    private final int maxDepth;

    private InputValueLimits(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    /**
     * Sets the limit used for subsequent schema validation and for requests without an override.
     * This does not revalidate existing schemas.
     */
    public static void setDefaultLimits(InputValueLimits limits) {
        defaultLimits = limits;
    }

    /**
     * Returns the JVM-wide default.
     */
    public static InputValueLimits getDefaultLimits() {
        return defaultLimits;
    }

    /**
     * Creates a builder initialized from the current JVM-wide default.
     */
    public static Builder newInputValueLimits() {
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
    public InputValueLimits transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @PublicApi
    @NullUnmarked
    public static class Builder {
        private int maxDepth;

        private Builder(InputValueLimits limits) {
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
        public InputValueLimits build() {
            return new InputValueLimits(maxDepth);
        }
    }
}
