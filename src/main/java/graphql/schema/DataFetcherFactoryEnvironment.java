package graphql.schema;

import graphql.PublicApi;

/**
 * This is passed to a {@link graphql.schema.DataFetcherFactory} when it is invoked to
 * get a {@link graphql.schema.DataFetcher}.
 *
 */
@PublicApi
public interface DataFetcherFactoryEnvironment {

    /**
     * @return the field that needs a {@link graphql.schema.DataFetcher}
     */
    GraphQLFieldDefinition getFieldDefinition();

    /**
     * @return the overall schema
     */
    GraphQLSchema getSchema();

}
