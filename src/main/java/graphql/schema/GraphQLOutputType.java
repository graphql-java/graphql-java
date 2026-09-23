package graphql.schema;


import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

/**
 * Output types represent those set of types that are allowed to be sent back as a graphql response, as opposed
 * to {@link graphql.schema.GraphQLInputType}s which can only be used as graphql mutation input.
 */
@PublicApi
@NullMarked
public interface GraphQLOutputType extends GraphQLType {
}
