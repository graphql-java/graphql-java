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

    /**
     * Returns {@code true} if {@code other} type is of the kind as this
     * one and wraps equivalent type.
     */
    @Override
    default boolean isEquivalentTo(GraphQLType other) {
        if ((other == null) || (other.getClass() != this.getClass())) {
            return false;
        }

        return getWrappedType().isEquivalentTo(((GraphQLModifiedType)other).getWrappedType());
    }
}
