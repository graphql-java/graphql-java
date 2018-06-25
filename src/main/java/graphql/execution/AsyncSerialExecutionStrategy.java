package graphql.execution;

import graphql.PublicApi;

/**
 * Async non-blocking execution, but serial: only one field at the the time will be resolved.
 * See {@link AsyncExecutionStrategy} for a non serial (parallel) execution of every field.
 */
@PublicApi
public class AsyncSerialExecutionStrategy extends AbstractAsyncExecutionStrategy {

    public AsyncSerialExecutionStrategy() {
        super(new SimpleDataFetcherExceptionHandler(), true);
    }

    public AsyncSerialExecutionStrategy(DataFetcherExceptionHandler exceptionHandler) {
        super(exceptionHandler, true);
    }

}
