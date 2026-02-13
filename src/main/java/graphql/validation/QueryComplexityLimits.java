package graphql.validation;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

/**
 * Configuration class for query complexity limits enforced during validation.
 * This provides a lightweight alternative to ExecutableNormalizedOperation (ENO) for tracking
 * query depth and field count.
 *
 * <p>By default, validation enforces limits (maxDepth=100, maxFieldsCount=100000).
 * To customize limits per-request, put a custom instance in the GraphQLContext:
 * <pre>{@code
 * QueryComplexityLimits limits = QueryComplexityLimits.newLimits()
 *     .maxDepth(10)
 *     .maxFieldsCount(100)
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
    public static final int DEFAULT_MAX_FIELDS_COUNT = 100_000;

    /**
     * The key used to store QueryComplexityLimits in GraphQLContext.
     */
    public static final String KEY = "graphql.validation.QueryComplexityLimits";

    /**
     * Standard limits (maxDepth=100, maxFieldsCount=100000).
     */
    public static final QueryComplexityLimits DEFAULT = new QueryComplexityLimits(DEFAULT_MAX_DEPTH, DEFAULT_MAX_FIELDS_COUNT);

    /**
     * No limits (all limits set to Integer.MAX_VALUE). Use this to disable complexity checking.
     */
    public static final QueryComplexityLimits NONE = new QueryComplexityLimits(Integer.MAX_VALUE, Integer.MAX_VALUE);

    private static volatile QueryComplexityLimits defaultLimits = DEFAULT;

    /**
     * Sets the default limits used when no limits are specified in GraphQLContext.
     * This is useful for testing or for applications that want different global defaults.
     *
     * @param limits the default limits to use (use {@link #NONE} to disable, {@link #DEFAULT} to restore)
     */
    public static void setDefaultLimits(QueryComplexityLimits limits) {
        defaultLimits = limits != null ? limits : DEFAULT;
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

    private QueryComplexityLimits(int maxDepth, int maxFieldsCount) {
        this.maxDepth = maxDepth;
        this.maxFieldsCount = maxFieldsCount;
    }

    /**
     * @return the maximum allowed depth for queries, where depth is measured as the number of nested Field nodes
     */
    public int getMaxDepth() {
        return maxDepth;
    }

    /**
     * @return the maximum allowed number of fields in a query, counting fields at each fragment spread site
     */
    public int getMaxFieldsCount() {
        return maxFieldsCount;
    }

    /**
     * @return a new builder for creating QueryComplexityLimits
     */
    public static Builder newLimits() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "QueryComplexityLimits{" +
                "maxDepth=" + maxDepth +
                ", maxFieldsCount=" + maxFieldsCount +
                '}';
    }

    /**
     * Builder for QueryComplexityLimits.
     */
    @PublicApi
    @NullMarked
    public static class Builder {
        private int maxDepth = Integer.MAX_VALUE;
        private int maxFieldsCount = Integer.MAX_VALUE;

        private Builder() {
        }

        /**
         * Sets the maximum allowed depth for queries.
         * Depth is measured as the number of nested Field nodes.
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
         * Sets the maximum allowed number of fields in a query.
         * Fields inside fragments are counted at each spread site.
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
         * @return a new QueryComplexityLimits instance
         */
        public QueryComplexityLimits build() {
            return new QueryComplexityLimits(maxDepth, maxFieldsCount);
        }
    }
}
