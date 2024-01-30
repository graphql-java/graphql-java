package graphql.execution;

import graphql.ExecutionResult;
import graphql.PublicSpi;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.util.concurrent.CompletableFuture;

/**
 * This is called when an exception is thrown during {@link graphql.schema.DataFetcher#get(DataFetchingEnvironment)} execution
 */
@PublicSpi
public interface DataFetcherExceptionHandler {

    /**
     * When an exception occurs during a call to a {@link DataFetcher} then this handler
     * is called to shape the errors that should be placed in the {@link ExecutionResult#getErrors()}
     * list of errors.
     *
     * @param handlerParameters the parameters to this callback
     *
     * @return a result that can contain custom formatted {@link graphql.GraphQLError}s
     */
    CompletableFuture<DataFetcherExceptionHandlerResult> handleException(DataFetcherExceptionHandlerParameters handlerParameters);
}
