package graphql.execution;

import graphql.PublicSpi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A provider of {@link ExecutionId}s
 */
@NullMarked
@PublicSpi
public interface ExecutionIdProvider {

    ExecutionIdProvider DEFAULT_EXECUTION_ID_PROVIDER = (query, operationName, context) -> ExecutionId.generate();


    /**
     * Allows provision of a unique identifier per query execution.
     *
     * @param query         the query to be executed
     * @param operationName thr name of the operation
     * @param context       the context object passed to the query
     *
     * @return a non null {@link ExecutionId}
     */
    ExecutionId provide(String query, @Nullable String operationName, @Nullable Object context);
}
