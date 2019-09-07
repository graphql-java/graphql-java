package graphql.schema;


import graphql.PublicApi;

/**
 * Output types represent those set of types that are allowed to be sent back as a graphql response, as opposed
 * to {@link GraphQLInputType}s which can only be used as graphql mutation input.
 */
@PublicApi
public interface GraphQLNamedOutputType extends GraphQLOutputType, GraphQLNamedType {
}
