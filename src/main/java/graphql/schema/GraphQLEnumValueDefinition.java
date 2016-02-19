package graphql.schema;


import static graphql.Assert.assertNotNull;

/**
 * <p>GraphQLEnumValueDefinition class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class GraphQLEnumValueDefinition {

    private final String name;
    private final String description;
    private final Object value;
    private final String deprecationReason;

    /**
     * <p>Constructor for GraphQLEnumValueDefinition.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param description a {@link java.lang.String} object.
     * @param value a {@link java.lang.Object} object.
     * @param deprecationReason a {@link java.lang.String} object.
     */
    public GraphQLEnumValueDefinition(String name, String description, Object value, String deprecationReason) {
        assertNotNull(name, "name can't be null");
        this.name = name;
        this.description = description;
        this.value = value;
        this.deprecationReason = deprecationReason;
    }

    /**
     * <p>Constructor for GraphQLEnumValueDefinition.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param description a {@link java.lang.String} object.
     * @param value a {@link java.lang.Object} object.
     */
    public GraphQLEnumValueDefinition(String name, String description, Object value) {
        this(name, description, value, null);
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
     * <p>Getter for the field <code>value</code>.</p>
     *
     * @return a {@link java.lang.Object} object.
     */
    public Object getValue() {
        return value;
    }

    /**
     * <p>isDeprecated.</p>
     *
     * @return a boolean.
     */
    public boolean isDeprecated() {
        return deprecationReason != null;
    }

    /**
     * <p>Getter for the field <code>deprecationReason</code>.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getDeprecationReason() {
        return deprecationReason;
    }
}
