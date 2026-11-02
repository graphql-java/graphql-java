package graphql.validation;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;

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
 * <p>By default, validation enforces limits (maxDepth=100, maxFieldsCount=10000,
 * maxFieldOccurrences=100000). Explicitly built limits replace these defaults;
 * unspecified builder limits are unlimited.
 * To customize limits per-request, put a custom instance in the GraphQLContext:
 * <pre>{@code
 * QueryComplexityLimits limits = QueryComplexityLimits.newLimits()
 *     .maxDepth(10)
 *     .maxFieldsCount(100)
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
     * Default maximum field count.
     */
    public static final int DEFAULT_MAX_FIELDS_COUNT = 10_000;

    /**
     * Default maximum number of field occurrences after fragment expansion.
     */
    public static final int DEFAULT_MAX_FIELD_OCCURRENCES = 100_000;

    /**
     * The key used to store QueryComplexityLimits in GraphQLContext.
     */
    public static final String KEY = "graphql.validation.QueryComplexityLimits";

    /**
     * Standard limits (maxDepth=100, maxFieldsCount=10000, maxFieldOccurrences=100000).
     */
    public static final QueryComplexityLimits DEFAULT = new QueryComplexityLimits(DEFAULT_MAX_DEPTH, DEFAULT_MAX_FIELDS_COUNT, DEFAULT_MAX_FIELD_OCCURRENCES);

    /**
     * No limits (all limits set to Integer.MAX_VALUE). Use this to disable complexity checking.
     */
    public static final QueryComplexityLimits NONE = new QueryComplexityLimits(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

    private static volatile QueryComplexityLimits defaultLimits = DEFAULT;

    /**
     * Sets the default limits used when no limits are specified in GraphQLContext.
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
    private final int maxFieldsCount;
    private final int maxFieldOccurrences;

    private QueryComplexityLimits(int maxDepth, int maxFieldsCount, int maxFieldOccurrences) {
        this.maxDepth = maxDepth;
        this.maxFieldsCount = maxFieldsCount;
        this.maxFieldOccurrences = maxFieldOccurrences;
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
    public int getMaxFieldsCount() {
        return maxFieldsCount;
    }

    /**
     * @return the maximum field occurrences in each operation after fragment expansion,
     * or {@link Integer#MAX_VALUE} when occurrence counting is disabled
     */
    public int getMaxFieldOccurrences() {
        return maxFieldOccurrences;
    }

    /**
     * @return a new builder with all limits initially unlimited
     */
    public static Builder newLimits() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "QueryComplexityLimits{" +
                "maxDepth=" + maxDepth +
                ", maxFieldsCount=" + maxFieldsCount +
                ", maxFieldOccurrences=" + maxFieldOccurrences +
                '}';
    }

    /**
     * Builder for QueryComplexityLimits.
     */
    @PublicApi
    @NullUnmarked
    public static class Builder {
        private int maxDepth = Integer.MAX_VALUE;
        private int maxFieldsCount = Integer.MAX_VALUE;
        private int maxFieldOccurrences = Integer.MAX_VALUE;

        private Builder() {
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
         * @param maxFieldsCount the maximum field count (must be positive)
         * @return this builder
         */
        public Builder maxFieldsCount(int maxFieldsCount) {
            if (maxFieldsCount <= 0) {
                throw new IllegalArgumentException("maxFieldsCount must be positive");
            }
            this.maxFieldsCount = maxFieldsCount;
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
         * @return a new QueryComplexityLimits instance
         */
        public QueryComplexityLimits build() {
            return new QueryComplexityLimits(maxDepth, maxFieldsCount, maxFieldOccurrences);
        }
    }
}
