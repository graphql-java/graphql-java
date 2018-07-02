package graphql.schema;


import graphql.PublicApi;

import java.util.Map;

import static graphql.Assert.assertNotNull;

/**
 * A modified type that indicates there is a list of the underlying wrapped type, eg a list of strings or a list of booleans.
 *
 * See http://graphql.org/learn/schema/#lists-and-non-null for more details on the concept
 */
@PublicApi
public class GraphQLList implements GraphQLType, GraphQLInputType, GraphQLOutputType, GraphQLModifiedType, GraphQLNullableType {

    /**
     * A factory method for creating list types so that when used with static imports allows
     * more readable code such as
     * {@code .type(list(GraphQLString)) }
     *
     * @param wrappedType the type to wrap as being a list
     *
     * @return a GraphQLList of that wrapped type
     */
    public static GraphQLList list(GraphQLType wrappedType) {
        return new GraphQLList(wrappedType);
    }

    private GraphQLType wrappedType;

    public GraphQLList(GraphQLType wrappedType) {
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

        GraphQLList that = (GraphQLList) o;

        return !(wrappedType != null ? !wrappedType.equals(that.wrappedType) : that.wrappedType != null);

    }

    @Override
    public int hashCode() {
        return wrappedType != null ? wrappedType.hashCode() : 0;
    }

    @Override
    public String getName() {
        return null;
    }
}
