package graphql.schema;


import static graphql.Assert.assertNotNull;

/**
 * A special type to allow a object/interface types to reference itself. It's replaced with the real type
 * object when the schema is build.
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class GraphQLTypeReference implements GraphQLType, GraphQLOutputType {

    private final String name;

    /**
     * <p>Constructor for GraphQLTypeReference.</p>
     *
     * @param name a {@link java.lang.String} object.
     */
    public GraphQLTypeReference(String name) {
        assertNotNull(name, "name can't be null");
        this.name = name;
    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getName() {
        return name;
    }
}
