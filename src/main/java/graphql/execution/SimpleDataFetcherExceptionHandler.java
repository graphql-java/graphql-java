package graphql.execution;

import graphql.ExceptionWhileDataFetching;
import graphql.PublicApi;
import graphql.language.SourceLocation;
import graphql.util.LogKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The standard handling of data fetcher error involves placing a {@link ExceptionWhileDataFetching} error
 * into the error collection
 */
@PublicApi
public class SimpleDataFetcherExceptionHandler implements DataFetcherExceptionHandler {

    private static final Logger logNotSafe = LogKit.getNotPrivacySafeLogger(SimpleDataFetcherExceptionHandler.class);

    @Override
    public DataFetcherExceptionHandlerResult onException(DataFetcherExceptionHandlerParameters handlerParameters) {
        Throwable exception = handlerParameters.getException();
        SourceLocation sourceLocation = handlerParameters.getSourceLocation();
        ExecutionPath path = handlerParameters.getPath();

        ExceptionWhileDataFetching error = new ExceptionWhileDataFetching(path, exception, sourceLocation);
        logNotSafe.warn(error.getMessage(), exception);

        return DataFetcherExceptionHandlerResult.newResult().error(error).build();
    }
}
