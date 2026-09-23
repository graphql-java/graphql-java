package graphql.schema;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

/**
 * A GraphQLType which is also a named element, which means it has a getName() method.
 */
@PublicApi
@NullMarked
public interface GraphQLNamedType extends GraphQLType, GraphQLNamedSchemaElement {


}
