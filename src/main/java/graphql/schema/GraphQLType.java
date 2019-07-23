package graphql.schema;


import graphql.PublicApi;

/**
 * A type inside the GraphQLSchema. A type doesn't have to have name, e.g. {@link GraphQLList}.
 *
 * See {@link GraphQLNamedType} for types with a name.
 */
@PublicApi
public interface GraphQLType extends GraphQLSchemaElement {
}
