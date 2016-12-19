package graphql.execution;

/**
 * A provider of {@link ExecutionId}s
 */
public interface ExecutionIdProvider {

    /**
     * Allows generation of a unique identifier per query execution
     *
     * @param query         the query to be executed
     * @param operationName thr name of the operation
     * @param context       the context object passed to the query
     * @return a non null {@link ExecutionId}
     */
    ExecutionId generate(String query, String operationName, Object context);
}