package graphql.schema;


import java.util.Map;

import static graphql.Assert.assertNotNull;

/**
 * <p>GraphQLList class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class GraphQLList implements GraphQLType, GraphQLInputType, GraphQLOutputType, GraphQLModifiedType, GraphQLNullableType {

    private GraphQLType wrappedType;

    /**
     * <p>Constructor for GraphQLList.</p>
     *
     * @param wrappedType a {@link graphql.schema.GraphQLType} object.
     */
    public GraphQLList(GraphQLType wrappedType) {
        assertNotNull(wrappedType, "wrappedType can't be null");
        this.wrappedType = wrappedType;
    }


    /**
     * <p>Getter for the field <code>wrappedType</code>.</p>
     *
     * @return a {@link graphql.schema.GraphQLType} object.
     */
    public GraphQLType getWrappedType() {
        return wrappedType;
    }

    void replaceTypeReferences(Map<String, GraphQLType> typeMap) {
        wrappedType = new SchemaUtil().resolveTypeReference(wrappedType, typeMap);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GraphQLList that = (GraphQLList) o;

        return !(wrappedType != null ? !wrappedType.equals(that.wrappedType) : that.wrappedType != null);

    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return wrappedType != null ? wrappedType.hashCode() : 0;
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return null;
    }
}
