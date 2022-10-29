package graphql.introspection;

import graphql.PublicApi;

@PublicApi
public interface IntrospectionQuery {
    String INTROSPECTION_QUERY = IntrospectionQueryBuilder.build();
}
