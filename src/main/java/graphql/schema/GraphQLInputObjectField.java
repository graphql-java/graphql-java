package graphql.schema;


import graphql.Internal;
import graphql.PublicApi;
import graphql.language.InputValueDefinition;

import java.util.Map;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;

@PublicApi
public class GraphQLInputObjectField {

    private final String name;
    private final String description;
    private GraphQLInputType type;
    private final Object defaultValue;
    private final InputValueDefinition definition;

    @Internal
    public GraphQLInputObjectField(String name, GraphQLInputType type) {
        this(name, null, type, null, null);
    }

    @Internal
    public GraphQLInputObjectField(String name, String description, GraphQLInputType type, Object defaultValue) {
        this(name,description,type,defaultValue,null);
    }

    @Internal
    public GraphQLInputObjectField(String name, String description, GraphQLInputType type, Object defaultValue, InputValueDefinition definition) {
        assertValidName(name);
        assertNotNull(type, "type can't be null");
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.description = description;
        this.definition = definition;
    }

    void replaceTypeReferences(Map<String, GraphQLType> typeMap) {
        type = (GraphQLInputType) new SchemaUtil().resolveTypeReference(type, typeMap);
    }

    public String getName() {
        return name;
    }

    public GraphQLInputType getType() { return type;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public String getDescription() {
        return description;
    }

    public InputValueDefinition getDefinition() {
        return definition;
    }

    public static Builder newInputObjectField() {
        return new Builder();
    }

    @PublicApi
    public static class Builder {
        private String name;
        private String description;
        private Object defaultValue;
        private GraphQLInputType type;
        private InputValueDefinition definition;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder definition(InputValueDefinition definition) {
            this.definition = definition;
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
            return new GraphQLInputObjectField(name, description, type, defaultValue, definition);
        }
    }
}