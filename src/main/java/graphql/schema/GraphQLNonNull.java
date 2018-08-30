package graphql.schema;


import graphql.PublicApi;

import java.util.Map;

import static graphql.Assert.assertNotNull;

/**
 * A modified type that indicates there the underlying wrapped type will not be null.
 *
 * See http://graphql.org/learn/schema/#lists-and-non-null for more details on the concept
 */
@PublicApi

public class GraphQLNonNull implements GraphQLType, GraphQLInputType, GraphQLOutputType, GraphQLModifiedType {

    /**
     * A factory method for creating non null types so that when used with static imports allows
     * more readable code such as
     * {@code .type(nonNull(GraphQLString)) }
     *
     * @param wrappedType the type to wrap as being non null
     *
     * @return a GraphQLNonNull of that wrapped type
     */
    public static GraphQLNonNull nonNull(GraphQLType wrappedType) {
        return new GraphQLNonNull(wrappedType);
    }

    private GraphQLType wrappedType;

    public GraphQLNonNull(GraphQLType wrappedType) {
        assertNotNull(wrappedType, "wrappedType can't be null");
        this.wrappedType = wrappedType;
    }

    @Override
    public GraphQLType getWrappedType() {
        return wrappedType;
    }

    void replaceTypeReferences(Map<String, GraphQLType> typeMap) {
        wrappedType = new SchemaUtil().resolveTypeReference(wrappedType, typeMap);
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
