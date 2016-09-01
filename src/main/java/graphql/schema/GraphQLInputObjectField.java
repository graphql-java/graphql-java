package graphql.schema;


import static graphql.Assert.assertNotNull;

public class GraphQLInputObjectField {

    private final String name;
    private final String description;
    private GraphQLInputType type;
    private final Object defaultValue;

    public GraphQLInputObjectField(String name, GraphQLInputType type) {
        this(name, null, type, null);
    }

    public GraphQLInputObjectField(String name, String description, GraphQLInputType type, Object defaultValue) {
        assertNotNull(name, "name can't be null");
        assertNotNull(type, "type can't be null");
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.description = description;
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

    public static Builder newInputObjectField() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private Object defaultValue;
        private GraphQLInputType type;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder type(GraphQLInputObjectType.Builder type) {
            return type(type.build());
        }

        public Builder type(GraphQLInputType type) {
            this.type = type;
            return this;
        }

        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public GraphQLInputObjectField build() {
            return new GraphQLInputObjectField(name, description, type, defaultValue);
        }
    }
}