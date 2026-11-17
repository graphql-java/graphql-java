package graphql.validation;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;

import java.util.function.Consumer;

/**
 * Configuration class for query complexity limits enforced during validation.
 * Limits the number and depth of merged fields using the same selections as field-merging validation.
 * Fields with the same response name (alias, or field name when unaliased) and a common parent type
 * count once at each response position. Their child selections are combined before counting.
 * For example, {@code { node { name name } }} has two fields, while
 * {@code { a: node { name } b: node { name } }} has four.
 * Repeated fragment spreads at the same response position merge; a fragment used at different
 * response positions contributes at each position.
 *
 * <p>Selections on different concrete parent types count separately. Interface and union selections
 * are included in each concrete group when concrete selections are present; otherwise they form
 * one group. This is a structural limit, not an estimate of resolver cost or list result sizes.
 * All selections count regardless of {@code @skip} or {@code @include}; validation does not coerce
 * variables or expand selections for each possible runtime object type.
 * Duplicate selections still require validation work even when they merge to one field.
 * Parser limits independently bound source size, token count and syntactic nesting.
 *
 * <p>Field occurrences count every field again at each fragment spread, without merging duplicate
 * selections. Fragment spreads and inline fragments do not themselves count as fields.
 * For example, {@code { node { name name } }} has three field occurrences and two merged fields.
 * Occurrences are limited separately for each operation, regardless of directives or runtime types.
 * Complexity limits are enforced independently of the validation-rule predicate.
 *
 * <p>The JVM-wide defaults initially enforce limits (maxDepth=100, maxMergedFieldCount=10000,
 * maxFieldOccurrences=100000), with introspection protections enabled at 500 merged fields and depth 20.
 * New builders copy all current JVM-wide settings; setting one limit preserves the others.
 * Request-specific settings override the JVM-wide configuration, including introspection enablement.
 * Use {@link #NONE} to disable both general complexity limits and introspection protections.
 *
 * <p>Introspection protections apply to each query selecting root {@code __schema} or {@code __type},
 * including through fragments and aliases. {@code __typename} alone does not activate them.
 * The smaller of the general and introspection-specific merged-field/depth limits applies to the
 * whole operation, including ordinary selections. The general occurrence limit also applies.
 * Each of the root {@code __schema} and {@code __type} coordinates, and {@code __Type.fields},
 * {@code __Type.inputFields}, {@code __Type.interfaces}, and {@code __Type.possibleTypes}, may
 * occur at most once after merging. Different aliases or response positions count separately.
 * Disabling introspection protections removes these additional caps and coordinate restrictions;
 * the general limits remain active. All these protections are independent of validation-rule selection.
 * Introspection merged-field, depth and coordinate violations retain the special
 * {@link graphql.introspection.BadFaithIntrospectionError} when executed through {@link graphql.GraphQL}.
 *
 * <p>
 * To customize limits per-request, put a custom instance in the GraphQLContext:
 * <pre>{@code
 * QueryComplexityLimits limits = QueryComplexityLimits.newLimitsWithDefaults()
 *     .maxDepth(10)
 *     .maxMergedFieldCount(100)
 *     .maxFieldOccurrences(100_000)
 *     .build();
 *
 * ExecutionInput executionInput = ExecutionInput.newExecutionInput()
 *     .query(query)
 *     .graphQLContext(ctx -> ctx.put(QueryComplexityLimits.KEY, limits))
 *     .build();
 * }</pre>
 *
 * <p>To disable limits for a request, use {@link #NONE}:
 * <pre>{@code
 * executionInput.getGraphQLContext().put(QueryComplexityLimits.KEY, QueryComplexityLimits.NONE);
 * }</pre>
 *
 * <p>To change the default limits globally (e.g., for testing), use {@link #setDefaultLimits(QueryComplexityLimits)}:
 * <pre>{@code
 * QueryComplexityLimits.setDefaultLimits(QueryComplexityLimits.NONE); // disable for tests
 * }</pre>
 */
@PublicApi
@NullMarked
public class QueryComplexityLimits {

    /**
     * Default maximum query depth.
     */
    public static final int DEFAULT_MAX_DEPTH = 100;

    /**
     * Default maximum merged field count.
     */
    public static final int DEFAULT_MAX_MERGED_FIELD_COUNT = 10_000;

    /**
     * Default maximum number of field occurrences after fragment expansion.
     */
    public static final int DEFAULT_MAX_FIELD_OCCURRENCES = 100_000;

    /** Whether introspection protections are enabled by default. */
    public static final boolean DEFAULT_INTROSPECTION_LIMITS_ENABLED = true;

    /** Default maximum merged field count for operations selecting root introspection. */
    public static final int DEFAULT_MAX_INTROSPECTION_MERGED_FIELD_COUNT = 500;

    /** Default maximum field depth for operations selecting root introspection. */
    public static final int DEFAULT_MAX_INTROSPECTION_DEPTH = 20;

    /**
     * The key used to store QueryComplexityLimits in GraphQLContext.
     */
    public static final String KEY = "graphql.validation.QueryComplexityLimits";

    /**
     * Standard general limits (depth 100, merged fields 10000, occurrences 100000),
     * with introspection protections enabled (merged fields 500, depth 20).
     */
    public static final QueryComplexityLimits DEFAULT = new Builder().build();

    /**
     * Disables general complexity limits and all introspection protections.
     * The introspection thresholds retain their library defaults, so transforming this instance
     * to re-enable introspection restores the default introspection protections.
     */
    public static final QueryComplexityLimits NONE = new Builder()
            .maxDepth(Integer.MAX_VALUE)
            .maxMergedFieldCount(Integer.MAX_VALUE)
            .maxFieldOccurrences(Integer.MAX_VALUE)
            .introspectionLimitsEnabled(false)
            .build();

    private static volatile QueryComplexityLimits defaultLimits = DEFAULT;

    /**
     * Sets the JVM-wide defaults used by new builders and by validation when no limits
     * are specified in GraphQLContext. Existing builders and limits remain unchanged.
     * This is useful for testing or for applications that want different global defaults.
     *
     * @param limits the default limits to use (use {@link #NONE} to disable, {@link #DEFAULT} to restore)
     */
    public static void setDefaultLimits(QueryComplexityLimits limits) {
        defaultLimits = limits;
    }

    /**
     * Returns the current default limits.
     *
     * @return the default limits
     */
    public static QueryComplexityLimits getDefaultLimits() {
        return defaultLimits;
    }

    private final int maxDepth;
    private final int maxMergedFieldCount;
    private final int maxFieldOccurrences;

    private final boolean introspectionLimitsEnabled;
    private final int maxIntrospectionMergedFieldCount;
    private final int maxIntrospectionDepth;

    private QueryComplexityLimits(Builder builder) {
        this.maxDepth = builder.maxDepth;
        this.maxMergedFieldCount = builder.maxMergedFieldCount;
        this.maxFieldOccurrences = builder.maxFieldOccurrences;
        this.introspectionLimitsEnabled = builder.introspectionLimitsEnabled;
        this.maxIntrospectionMergedFieldCount = builder.maxIntrospectionMergedFieldCount;
        this.maxIntrospectionDepth = builder.maxIntrospectionDepth;
    }

    /**
     * @return the maximum allowed depth for queries, where depth is the number of fields on the deepest response path
     */
    public int getMaxDepth() {
        return maxDepth;
    }

    /**
     * @return the maximum allowed number of merged fields in each operation
     */
    public int getMaxMergedFieldCount() {
        return maxMergedFieldCount;
    }

    /**
     * @return the maximum field occurrences in each operation after fragment expansion,
     * or {@link Integer#MAX_VALUE} when occurrence counting is disabled
     */
    public int getMaxFieldOccurrences() {
        return maxFieldOccurrences;
    }

    /** @return whether additional introspection caps and restricted-coordinate checks are enabled */
    public boolean isIntrospectionLimitsEnabled() {
        return introspectionLimitsEnabled;
    }

    /** @return the maximum merged field count for an operation selecting root introspection */
    public int getMaxIntrospectionMergedFieldCount() {
        return maxIntrospectionMergedFieldCount;
    }

    /** @return the maximum field depth for an operation selecting root introspection */
    public int getMaxIntrospectionDepth() {
        return maxIntrospectionDepth;
    }

    /**
     * Creates modified limits from this instance, independently of the current JVM-wide defaults.
     *
     * @param builderConsumer the changes to apply
     * @return a new limits instance
     */
    public QueryComplexityLimits transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    /**
     * Creates a builder initialized with the current JVM-wide limits from {@link #getDefaultLimits()}.
     * The defaults are copied when this method is called. Later changes through
     * {@link #setDefaultLimits(QueryComplexityLimits)} do not affect this builder or its built instances.
     *
     * @return a new builder initialized with the current JVM-wide defaults
     */
    public static Builder newLimitsWithDefaults() {
        return new Builder(getDefaultLimits());
    }

    @Override
    public String toString() {
        return "QueryComplexityLimits{" +
                "maxDepth=" + maxDepth +
                ", maxMergedFieldCount=" + maxMergedFieldCount +
                ", maxFieldOccurrences=" + maxFieldOccurrences +
                ", introspectionLimitsEnabled=" + introspectionLimitsEnabled +
                ", maxIntrospectionMergedFieldCount=" + maxIntrospectionMergedFieldCount +
                ", maxIntrospectionDepth=" + maxIntrospectionDepth +
                '}';
    }

    /**
     * Builder for QueryComplexityLimits.
     */
    @PublicApi
    @NullUnmarked
    public static class Builder {
        private int maxDepth = DEFAULT_MAX_DEPTH;
        private int maxMergedFieldCount = DEFAULT_MAX_MERGED_FIELD_COUNT;
        private int maxFieldOccurrences = DEFAULT_MAX_FIELD_OCCURRENCES;
        private boolean introspectionLimitsEnabled = DEFAULT_INTROSPECTION_LIMITS_ENABLED;
        private int maxIntrospectionMergedFieldCount = DEFAULT_MAX_INTROSPECTION_MERGED_FIELD_COUNT;
        private int maxIntrospectionDepth = DEFAULT_MAX_INTROSPECTION_DEPTH;

        private Builder() {
        }

        private Builder(QueryComplexityLimits limits) {
            this.maxDepth = limits.maxDepth;
            this.maxMergedFieldCount = limits.maxMergedFieldCount;
            this.maxFieldOccurrences = limits.maxFieldOccurrences;
            this.introspectionLimitsEnabled = limits.introspectionLimitsEnabled;
            this.maxIntrospectionMergedFieldCount = limits.maxIntrospectionMergedFieldCount;
            this.maxIntrospectionDepth = limits.maxIntrospectionDepth;
        }

        /**
         * Sets the maximum allowed depth for queries.
         * Depth is measured as the number of fields on the deepest response path.
         *
         * @param maxDepth the maximum depth (must be positive)
         * @return this builder
         */
        public Builder maxDepth(int maxDepth) {
            if (maxDepth <= 0) {
                throw new IllegalArgumentException("maxDepth must be positive");
            }
            this.maxDepth = maxDepth;
            return this;
        }

        /**
         * Sets the maximum allowed number of merged fields in each operation.
         * Merged child selections contribute again at each distinct response position.
         *
         * @param maxMergedFieldCount the maximum merged field count (must be positive)
         * @return this builder
         */
        public Builder maxMergedFieldCount(int maxMergedFieldCount) {
            if (maxMergedFieldCount <= 0) {
                throw new IllegalArgumentException("maxMergedFieldCount must be positive");
            }
            this.maxMergedFieldCount = maxMergedFieldCount;
            return this;
        }

        /**
         * Sets the maximum number of field occurrences in each operation. A fragment's fields
         * contribute at every spread, even when their response names merge. All selections count
         * regardless of {@code @skip} or {@code @include}. Fragment definitions that are not
         * referenced by an operation do not contribute to its count.
         *
         * @param maxFieldOccurrences the positive limit, or {@link Integer#MAX_VALUE} to disable it
         * @return this builder
         */
        public Builder maxFieldOccurrences(int maxFieldOccurrences) {
            if (maxFieldOccurrences <= 0) {
                throw new IllegalArgumentException("maxFieldOccurrences must be positive");
            }
            this.maxFieldOccurrences = maxFieldOccurrences;
            return this;
        }

        /**
         * Enables or disables additional introspection caps and restricted-coordinate checks.
         * Changing this flag preserves the configured thresholds and the general limits.
         *
         * @param enabled whether introspection protections are enabled
         * @return this builder
         */
        public Builder introspectionLimitsEnabled(boolean enabled) {
            this.introspectionLimitsEnabled = enabled;
            return this;
        }

        /**
         * Sets the merged field cap for the whole operation when it selects root introspection.
         * The smaller of this cap and the general merged field cap applies when enabled.
         *
         * @param maxIntrospectionMergedFieldCount the positive introspection field limit
         * @return this builder
         */
        public Builder maxIntrospectionMergedFieldCount(int maxIntrospectionMergedFieldCount) {
            if (maxIntrospectionMergedFieldCount <= 0) {
                throw new IllegalArgumentException("maxIntrospectionMergedFieldCount must be positive");
            }
            this.maxIntrospectionMergedFieldCount = maxIntrospectionMergedFieldCount;
            return this;
        }

        /**
         * Sets the field depth cap for the whole operation when it selects root introspection.
         * The smaller of this cap and the general depth cap applies when enabled.
         *
         * @param maxIntrospectionDepth the positive introspection depth limit
         * @return this builder
         */
        public Builder maxIntrospectionDepth(int maxIntrospectionDepth) {
            if (maxIntrospectionDepth <= 0) {
                throw new IllegalArgumentException("maxIntrospectionDepth must be positive");
            }
            this.maxIntrospectionDepth = maxIntrospectionDepth;
            return this;
        }

        /**
         * @return a new QueryComplexityLimits instance
         */
        public QueryComplexityLimits build() {
            return new QueryComplexityLimits(this);
        }
    }
}
