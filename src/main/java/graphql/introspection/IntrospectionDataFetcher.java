package graphql.introspection;

import graphql.Internal;

/**
 * Special DataFetcher which is only used inside {@link Introspection}
 */
@Internal
public interface IntrospectionDataFetcher {

    Object get(IntrospectionDataFetchingEnvironment env);
}
