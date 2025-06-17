package graphql.util.querygenerator;

import graphql.ExperimentalApi;

/**
 * Represents the result of a query generation process.
 */
@ExperimentalApi
public class QueryGeneratorResult {
    private final String query;
    private final String usedType;
    private final int totalFieldCount;
    private final boolean reachedMaxFieldCount;

    public QueryGeneratorResult(
            String query,
            String usedType,
            int totalFieldCount,
            boolean reachedMaxFieldCount
    ) {
        this.query = query;
        this.usedType = usedType;
        this.totalFieldCount = totalFieldCount;
        this.reachedMaxFieldCount = reachedMaxFieldCount;
    }

    /**
     * Returns the generated query string.
     *
     * @return the query string
     */
    public String getQuery() {
        return query;
    }

    /**
     * Returns the type that ultimately was used to generate the query.
     *
     * @return the used type
     */
    public String getUsedType() {
        return usedType;
    }

    /**
     * Returns the total number of fields that were considered during query generation.
     *
     * @return the total field count
     */
    public int getTotalFieldCount() {
        return totalFieldCount;
    }

    /**
     * Indicates whether the maximum field count was reached during query generation.
     *
     * @return true if the maximum field count was reached, false otherwise
     */
    public boolean isReachedMaxFieldCount() {
        return reachedMaxFieldCount;
    }
}
