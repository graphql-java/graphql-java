package graphql.introspection;

import graphql.Internal;
import graphql.TrivialDataFetcher;

/**
 * Special DataFetcher which is only used inside {@link Introspection}
 */
@Internal
public interface IntrospectionDataFetcher<T> extends TrivialDataFetcher<T> {
}
