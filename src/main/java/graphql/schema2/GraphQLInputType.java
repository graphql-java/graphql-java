package graphql.schema2;


import graphql.PublicApi;
import graphql.schema.GraphQLType;

/**
 * Input types represent those set of types that are allowed to be accepted as graphql mutation input, as opposed
 * to {@link graphql.schema.GraphQLOutputType}s which can only be used as graphql response output.
 */
@PublicApi
public interface GraphQLInputType extends GraphQLType {
}
