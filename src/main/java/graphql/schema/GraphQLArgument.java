package graphql.schema;


import static graphql.Assert.assertNotNull;

/**
 * <p>GraphQLArgument class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class GraphQLArgument {

    private final String name;
    private final String description;
    private GraphQLInputType type;
    private final Object defaultValue;

    /**
     * <p>Constructor for GraphQLArgument.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param description a {@link java.lang.String} object.
     * @param type a {@link graphql.schema.GraphQLInputType} object.
     * @param defaultValue a {@link java.lang.Object} object.
     */
    public GraphQLArgument(String name, String description, GraphQLInputType type, Object defaultValue) {
        assertNotNull(name, "name can't be null");
        assertNotNull(type, "type can't be null");
        this.name = name;
        this.description = description;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    /**
     * <p>Constructor for GraphQLArgument.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param type a {@link graphql.schema.GraphQLInputType} object.
     */
    public GraphQLArgument(String name, GraphQLInputType type) {
        this(name, null, type, null);
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
     * <p>Getter for the field <code>type</code>.</p>
     *
     * @return a {@link graphql.schema.GraphQLInputType} object.
     */
    public GraphQLInputType getType() {
        return type;
    }

    /**
     * <p>Getter for the field <code>defaultValue</code>.</p>
     *
     * @return a {@link java.lang.Object} object.
     */
    public Object getDefaultValue() {
        return defaultValue;
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
     * <p>newArgument.</p>
     *
     * @return a {@link graphql.schema.GraphQLArgument.Builder} object.
     */
    public static Builder newArgument() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private GraphQLInputType type;
        private Object defaultValue;
        private String description;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }


        public Builder type(GraphQLInputType type) {
            this.type = type;
            return this;
        }

        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public GraphQLArgument build() {
            return new GraphQLArgument(name, description, type, defaultValue);
        }
    }


}
