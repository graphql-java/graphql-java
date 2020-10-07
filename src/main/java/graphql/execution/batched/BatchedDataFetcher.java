package graphql.execution.batched;

import graphql.PublicApi;
import graphql.schema.DataFetcher;

/**
 * See {@link Batched}.
 * @deprecated This has been deprecated in favour of using {@link graphql.execution.AsyncExecutionStrategy} and {@link graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation}
 */
@Deprecated
@PublicApi
public interface BatchedDataFetcher extends DataFetcher {
    // Marker interface
}
