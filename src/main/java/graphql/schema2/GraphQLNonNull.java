package graphql.schema2;


import graphql.PublicApi;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLModifiedType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLType;

import static graphql.Assert.assertNotNull;

/**
 * A modified type that indicates there the underlying wrapped type will not be null.
 *
 * See http://graphql.org/learn/schema/#lists-and-non-null for more details on the concept
 */
@PublicApi
public class GraphQLNonNull implements GraphQLType, GraphQLInputType, GraphQLOutputType, GraphQLModifiedType {

    private final TypeReference wrappedType;

    public GraphQLNonNull(TypeReference wrappedType) {
        assertNotNull(wrappedType, "wrappedType can't be null");
        this.wrappedType = wrappedType;
    }

    @Override
    public TypeReference getWrappedType() {
        return wrappedType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GraphQLNonNull that = (GraphQLNonNull) o;

        return !(wrappedType != null ? !wrappedType.equals(that.wrappedType) : that.wrappedType != null);

    }

    @Override
    public int hashCode() {
        return wrappedType != null ? wrappedType.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "GraphQLNonNull{" +
                "wrappedType=" + wrappedType +
                '}';
    }

    @Override
    public String getName() {
        return null;
    }
}
