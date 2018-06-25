package graphql.execution;

import graphql.PublicApi;

/**
 * The standard graphql execution strategy that runs fields asynchronously non-blocking.
 */
@PublicApi
public class AsyncExecutionStrategy extends AbstractAsyncExecutionStrategy {


    /**
     * The standard graphql execution strategy that runs fields asynchronously
     */
    public AsyncExecutionStrategy() {
        super(new SimpleDataFetcherExceptionHandler());
    }

    /**
     * Creates a execution strategy that uses the provided exception handler
     *
     * @param exceptionHandler the exception handler to use
     */
    public AsyncExecutionStrategy(DataFetcherExceptionHandler exceptionHandler) {
        super(exceptionHandler);
    }

}
