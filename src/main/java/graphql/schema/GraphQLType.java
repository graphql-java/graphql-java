package graphql.schema;


import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

/**
 * A type inside the GraphQLSchema. A type doesn't have to have name, e.g. {@link GraphQLList}.
 * <p>
 * See {@link GraphQLNamedType} for types with a name.
 */
@PublicApi
@NullMarked
public interface GraphQLType extends GraphQLSchemaElement {
}
