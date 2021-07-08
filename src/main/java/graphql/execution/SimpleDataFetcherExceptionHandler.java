package graphql.execution;

import graphql.ExceptionWhileDataFetching;
import graphql.GraphQLError;
import graphql.PublicApi;
import graphql.util.LogKit;
import org.slf4j.Logger;

import java.util.concurrent.CompletionException;

/**
 * The standard handling of data fetcher error involves placing a {@link ExceptionWhileDataFetching} error
 * into the error collection
 */
@PublicApi
public class SimpleDataFetcherExceptionHandler implements DataFetcherExceptionHandler {

    private static final Logger logNotSafe = LogKit.getNotPrivacySafeLogger(SimpleDataFetcherExceptionHandler.class);

    @Override
    public DataFetcherExceptionHandlerResult onException(DataFetcherExceptionHandlerParameters handlerParameters) {
        Throwable exception = unwrap(handlerParameters.getException());
        GraphQLError error;
        if (exception instanceof GraphQLError) {
            error = (GraphQLError) exception;
        } else {
            error = new ExceptionWhileDataFetching(handlerParameters.getPath(), exception, handlerParameters.getSourceLocation());
        }
        logNotSafe.warn(error.getMessage(), exception);
        return DataFetcherExceptionHandlerResult.newResult().error(error).build();
    }

    /**
     * Called to unwrap an exception to a more suitable cause if required
     *
     * @param exception the exception to unwrap
     *
     * @return the suitable exception
     */
    protected Throwable unwrap(Throwable exception) {
        if (exception instanceof CompletionException) {
            return exception.getCause();
        }
        return exception;
    }
}
