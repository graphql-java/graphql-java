package graphql.schema;


import graphql.PublicApi;

/**
 * A modified type wraps another graphql type and modifies it behavior
 *
 * @see graphql.schema.GraphQLNonNull
 * @see graphql.schema.GraphQLList
 */
@PublicApi
public interface GraphQLModifiedType extends GraphQLType {

    GraphQLType getWrappedType();
}
