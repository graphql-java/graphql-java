package graphql.schema;

import graphql.PublicApi;

/**
 * A GraphQLType which is also a named element, which means it has a getName() method.
 */
@PublicApi
public interface GraphQLNamedType extends GraphQLType, GraphQLNamedSchemaElement {


}
