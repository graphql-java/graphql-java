package graphql.schema;


import java.util.Map;

import static graphql.Assert.assertNotNull;

/**
 * <p>GraphQLNonNull class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class GraphQLNonNull implements GraphQLType, GraphQLInputType, GraphQLOutputType, GraphQLModifiedType {

    private  GraphQLType wrappedType;

    /**
     * <p>Constructor for GraphQLNonNull.</p>
     *
     * @param wrappedType a {@link graphql.schema.GraphQLType} object.
     */
    public GraphQLNonNull(GraphQLType wrappedType) {
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

        GraphQLNonNull that = (GraphQLNonNull) o;

        return !(wrappedType != null ? !wrappedType.equals(that.wrappedType) : that.wrappedType != null);

    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return wrappedType != null ? wrappedType.hashCode() : 0;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "GraphQLNonNull{" +
                "wrappedType=" + wrappedType +
                '}';
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return null;
    }
}
