package graphql.schema;


import static graphql.Assert.assertNotNull;

/**
 * <p>GraphQLScalarType class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class GraphQLScalarType implements GraphQLType, GraphQLInputType, GraphQLOutputType, GraphQLUnmodifiedType,GraphQLNullableType {

    private final String name;
    private final String description;
    private final Coercing coercing;


    /**
     * <p>Constructor for GraphQLScalarType.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param description a {@link java.lang.String} object.
     * @param coercing a {@link graphql.schema.Coercing} object.
     */
    public GraphQLScalarType(String name, String description, Coercing coercing) {
        assertNotNull(name, "name can't be null");
        assertNotNull(coercing, "coercing can't be null");
        this.name = name;
        this.description = description;
        this.coercing = coercing;
    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getName() {
        return name;
    }


    /**
     * <p>Getter for the field <code>description</code>.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getDescription() {
        return description;
    }


    /**
     * <p>Getter for the field <code>coercing</code>.</p>
     *
     * @return a {@link graphql.schema.Coercing} object.
     */
    public Coercing getCoercing() {
        return coercing;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "GraphQLScalarType{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", coercing=" + coercing +
                '}';
    }
}
