package graphql.schema;


import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

/**
 * A modified type wraps another graphql type and modifies it behavior
 *
 * @see graphql.schema.GraphQLNonNull
 * @see graphql.schema.GraphQLList
 */
@PublicApi
@NullMarked
public interface GraphQLModifiedType extends GraphQLType {

    GraphQLType getWrappedType();
}
