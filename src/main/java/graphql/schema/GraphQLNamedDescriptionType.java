package graphql.schema;

import graphql.PublicApi;

/**
 * A GraphQLType with name and description.
 */
@PublicApi
public interface GraphQLNamedDescriptionType extends GraphQLNamedType {

    String getDescription();

}
