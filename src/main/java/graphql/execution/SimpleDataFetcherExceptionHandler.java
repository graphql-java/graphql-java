package graphql.execution;

import graphql.ExceptionWhileDataFetching;
import graphql.PublicApi;
import graphql.language.SourceLocation;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * The standard handling of data fetcher error involves placing a {@link ExceptionWhileDataFetching} error
 * into the error collection
 */
@PublicApi
@NullMarked
public class SimpleDataFetcherExceptionHandler implements DataFetcherExceptionHandler {

    static final SimpleDataFetcherExceptionHandler defaultImpl = new SimpleDataFetcherExceptionHandler();

    private DataFetcherExceptionHandlerResult handleExceptionImpl(DataFetcherExceptionHandlerParameters handlerParameters) {
        Throwable exception = unwrap(handlerParameters.getException());
        SourceLocation sourceLocation = handlerParameters.getSourceLocation();
        ResultPath path = handlerParameters.getPath();

        ExceptionWhileDataFetching error = new ExceptionWhileDataFetching(path, exception, sourceLocation);
        logException(error, exception);

        return DataFetcherExceptionHandlerResult.newResult().error(error).build();
    }

    @Override
    public CompletableFuture<DataFetcherExceptionHandlerResult> handleException(DataFetcherExceptionHandlerParameters handlerParameters) {
        return CompletableFuture.completedFuture(handleExceptionImpl(handlerParameters));
    }

    /**
     * Called to log the exception - a subclass could choose to something different in logging terms
     *
     * @param error     the graphql error
     * @param exception the exception that happened
     */
    protected void logException(ExceptionWhileDataFetching error, Throwable exception) {
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
