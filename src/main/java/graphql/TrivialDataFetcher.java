package graphql;

import graphql.schema.DataFetcher;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;


/**
 * Mark a DataFetcher as trivial:
 * If a data fetcher is simply mapping data from an object to a field, it can be considered a trivial data fetcher for the purposes
 * of tracing and so on.
 */
@NullMarked
@PublicSpi
public interface TrivialDataFetcher<T extends @Nullable Object> extends DataFetcher<T> {
}
