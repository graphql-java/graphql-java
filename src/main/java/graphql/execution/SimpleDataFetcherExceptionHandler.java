package graphql.execution;

import graphql.ExceptionWhileDataFetching;
import graphql.PublicApi;
import graphql.language.SourceLocation;
import graphql.util.LogKit;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * The standard handling of data fetcher error involves placing a {@link ExceptionWhileDataFetching} error
 * into the error collection
 */
@PublicApi
public class SimpleDataFetcherExceptionHandler implements DataFetcherExceptionHandler {

    private static final Logger logNotSafe = LogKit.getNotPrivacySafeLogger(SimpleDataFetcherExceptionHandler.class);

    static final SimpleDataFetcherExceptionHandler defaultImpl = new SimpleDataFetcherExceptionHandler();

    @Override
    public DataFetcherExceptionHandlerResult onException(DataFetcherExceptionHandlerParameters handlerParameters) {
        Throwable exception = unwrap(handlerParameters.getException());
        SourceLocation sourceLocation = handlerParameters.getSourceLocation();
        ResultPath path = handlerParameters.getPath();

        ExceptionWhileDataFetching error = new ExceptionWhileDataFetching(path, exception, sourceLocation);
        logException(error, exception);

        return DataFetcherExceptionHandlerResult.newResult().error(error).build();
    }

    @Override
    public CompletableFuture<DataFetcherExceptionHandlerResult> handleException(DataFetcherExceptionHandlerParameters handlerParameters) {
        return CompletableFuture.completedFuture(onException(handlerParameters));
    }

    /**
     * Called to log the exception - a subclass could choose to something different in logging terms
     *
     * @param error     the graphql error
     * @param exception the exception that happened
     */
    protected void logException(ExceptionWhileDataFetching error, Throwable exception) {
        logNotSafe.warn(error.getMessage(), exception);
    }

    /**
     * Called to unwrap an exception to a more suitable cause if required.
     *
     * @param exception the exception to unwrap
     *
     * @return the suitable exception
     */
    protected Throwable unwrap(Throwable exception) {
        if (exception.getCause() != null) {
            if (exception instanceof CompletionException) {
                return exception.getCause();
            }
        }
        return exception;
    }
}
