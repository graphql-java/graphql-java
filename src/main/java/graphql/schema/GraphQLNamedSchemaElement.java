package graphql.schema;

import graphql.PublicApi;

/**
 * A Schema element which has also name.
 */
@PublicApi
public interface GraphQLNamedSchemaElement extends GraphQLSchemaElement {

    String getName();
}
