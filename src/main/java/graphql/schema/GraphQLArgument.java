package graphql.schema;


import graphql.PublicApi;
import graphql.language.InputValueDefinition;

import java.util.Map;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;

/**
 * This defines an argument that can be supplied to a graphql field (via {@link graphql.schema.GraphQLFieldDefinition}.
 *
 * Fields can be thought of as "functions" that take arguments and return a value.
 *
 * See http://graphql.org/learn/queries/#arguments for more details on the concept.
 */
@PublicApi
public class GraphQLArgument {

    private final String name;
    private final String description;
    private GraphQLInputType type;
    private final Object value;
    private final Object defaultValue;
    private final InputValueDefinition definition;

    public GraphQLArgument(String name, String description, GraphQLInputType type, Object defaultValue) {
        this(name, description, type, defaultValue, null);
    }

    public GraphQLArgument(String name, GraphQLInputType type) {
        this(name, null, type, null, null);
    }

    public GraphQLArgument(String name, String description, GraphQLInputType type, Object defaultValue, InputValueDefinition definition) {
        this(name, description, type, defaultValue, null, definition);
    }

    private GraphQLArgument(String name, String description, GraphQLInputType type, Object defaultValue, Object value, InputValueDefinition definition) {
        assertValidName(name);
        assertNotNull(type, "type can't be null");
        this.name = name;
        this.description = description;
        this.type = type;
        this.defaultValue = defaultValue;
        this.value = value;
        this.definition = definition;
    }


    void replaceTypeReferences(Map<String, GraphQLType> typeMap) {
        type = (GraphQLInputType) new SchemaUtil().resolveTypeReference(type, typeMap);
    }

    public String getName() {
        return name;
    }

    public GraphQLInputType getType() {
        return type;
    }

    /**
     * An argument has a default value when it represents the logical argument structure that a {@link graphql.schema.GraphQLFieldDefinition}
     * can have and it can also have a default value when used in a schema definition language (SDL) where the
     * default value comes via the directive definition.
     *
     * @return the default value of an argument
     */
    public Object getDefaultValue() {
        return defaultValue;
    }

    /**
     * An argument ONLY has a value when its used in a schema definition language (SDL) context as the arguments to SDL directives.  The method
     * should not be called in a query context, but rather the AST / variables map should be used to obtain an arguments value.
     *
     * @return the argument value
     */
    public Object getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public InputValueDefinition getDefinition() {
        return definition;
    }

    public static Builder newArgument() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private GraphQLInputType type;
        private Object defaultValue;
        private Object value;
        private String description;
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


        public Builder type(GraphQLInputType type) {
            this.type = type;
            return this;
        }

        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder value(Object value) {
            this.value = value;
            return this;
        }

        public GraphQLArgument build() {
            return new GraphQLArgument(name, description, type, defaultValue, value, definition);
        }
    }
}
