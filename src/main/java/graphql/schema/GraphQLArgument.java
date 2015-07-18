package graphql.schema;


import static graphql.Assert.assertNotNull;

public class GraphQLArgument {

    private final String name;
    private final String description;
    private GraphQLInputType type;
    private final Object defaultValue;

    public GraphQLArgument(String name, String description, GraphQLInputType type, Object defaultValue) {
        assertNotNull(name, "name can't be null");
        assertNotNull(type, "type can't be null");
        this.name = name;
        this.description = description;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    public GraphQLArgument(String name, GraphQLInputType type) {
        this(name, null, type, null);
    }


    public String getName() {
        return name;
    }

    public GraphQLInputType getType() {
        return type;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }


    public String getDescription() {
        return description;
    }

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
