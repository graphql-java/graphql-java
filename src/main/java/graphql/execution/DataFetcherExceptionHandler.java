package graphql.execution;

import graphql.ExecutionResult;
import graphql.PublicSpi;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
     *
     * @deprecated use {@link #handleException(DataFetcherExceptionHandlerParameters)} instead which as an asynchronous
     * version
     */
    @Deprecated
    default DataFetcherExceptionHandlerResult onException(DataFetcherExceptionHandlerParameters handlerParameters) {
        return SimpleDataFetcherExceptionHandler.defaultImpl.onException(handlerParameters);
    }

    /**
     * When an exception occurs during a call to a {@link DataFetcher} then this handler
     * is called to shape the errors that should be placed in the {@link ExecutionResult#getErrors()}
     * list of errors.
     *
     * @param handlerParameters the parameters to this callback
     *
     * @return a result that can contain custom formatted {@link graphql.GraphQLError}s
     */
    default CompletableFuture<DataFetcherExceptionHandlerResult> handleException(DataFetcherExceptionHandlerParameters handlerParameters) {
        DataFetcherExceptionHandlerResult result = onException(handlerParameters);
        return CompletableFuture.completedFuture(result);
    }
}
