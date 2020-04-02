package graphql.introspection;

import graphql.PublicApi;

@PublicApi
public class IntrospectionUtils {
    public static final String INTROSPECTION_SCHEMA_STRING = "__schema";

    /**
     * Tests whether the provided {@code query} represents a GraphQL introspection query or not.  Whether or not
     * the query is the introspection query is determined by the following in the order given:
     *
     * 1.  If the operation name is equal {@link IntrospectionQuery#INTROSPECTION_QUERY_NAME} or
     * 2.  If query contains operation name {@link IntrospectionQuery#INTROSPECTION_QUERY_NAME} or
     * 3.  If query contains {@link IntrospectionUtils#INTROSPECTION_SCHEMA_STRING}
     *
     * @param operationName The operation name of the query.
     * @param query         Optional query to test.  If provided, and the operationName is not
     *                      IntrospectionQuery.INTROSPECTION_QUERY_NAME, then query is scanned to check for the
     *                      name or the existence of __schema
     *
     * @return {@code true} if the operation name or query represent the introspection query, false otherwise.
     */
    public static boolean isIntrospectionQuery(final String operationName, final String query) {
        boolean isIntrospectionQueryName = IntrospectionQuery.INTROSPECTION_QUERY_NAME.equals(operationName);

        return isIntrospectionQueryName ||
            ((null != query) &&
             !query.isEmpty() &&
             (query.contains(IntrospectionQuery.INTROSPECTION_QUERY_NAME) || query.contains(IntrospectionUtils.INTROSPECTION_SCHEMA_STRING)));
    }
}
