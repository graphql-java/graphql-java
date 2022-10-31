package graphql.introspection;

import graphql.PublicApi;

@PublicApi
public interface IntrospectionQuery {
    /**
     * This is the default introspection query provided by graphql-java
     *
     * @see IntrospectionQueryBuilder for ways to customize the introspection query
     */
    String INTROSPECTION_QUERY = IntrospectionQueryBuilder.build();
}
