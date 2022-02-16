package graphql.execution.nextgen;

import graphql.Internal;
import graphql.schema.DataFetcher;

/**
 * @deprecated Jan 2022 - We have decided to deprecate the NextGen engine, and it will be removed in a future release.
 */
@Deprecated
@Internal
public interface BatchedDataFetcher<T> extends DataFetcher<T> {
}
