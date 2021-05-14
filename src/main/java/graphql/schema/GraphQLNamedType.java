package graphql.schema;

import graphql.PublicApi;

/**
 * A GraphQLType which is also a named element, which means it has a getName() method.
 */
@PublicApi
public interface GraphQLNamedType extends GraphQLType, GraphQLNamedSchemaElement {

    /**
     * Returns {@code true} if {@code other} type is of the kind and name as this one.
     */
    @Override
    default boolean isEquivalentTo(final GraphQLType other) {
        if ((other == null) || (other.getClass() != this.getClass())) {
            return false;
        }

        return ((GraphQLNamedType)other).getName().equals(this.getName());
    }
}
