package graphql.schema;


import graphql.PublicApi;
import graphql.language.InputValueDefinition;

import java.util.Map;
import java.util.function.Consumer;

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
    private final Object defaultValue;
    private final InputValueDefinition definition;

    public GraphQLArgument(String name, String description, GraphQLInputType type, Object defaultValue) {
        this(name, description, type, defaultValue, null);
    }

    public GraphQLArgument(String name, GraphQLInputType type) {
        this(name, null, type, null, null);
    }

    public GraphQLArgument(String name, String description, GraphQLInputType type, Object defaultValue, InputValueDefinition definition) {
        assertValidName(name);
        assertNotNull(type, "type can't be null");
        this.name = name;
        this.description = description;
        this.type = type;
        this.defaultValue = defaultValue;
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

    public Object getDefaultValue() {
        return defaultValue;
    }


    public String getDescription() {
        return description;
    }

    public InputValueDefinition getDefinition() {
        return definition;
    }

    /**
     * This helps you transform the current GraphQLArgument into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new field based on calling build on that builder
     */
    public GraphQLArgument transform(Consumer<Builder> builderConsumer) {
        Builder builder = newArgument(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static Builder newArgument() {
        return new Builder();
    }

    public static Builder newArgument(GraphQLArgument existing) {
        return new Builder(existing);
    }

    public static class Builder {

        private String name;
        private GraphQLInputType type;
        private Object defaultValue;
        private String description;
        private InputValueDefinition definition;

        public Builder() {
        }

        public Builder(GraphQLArgument existing) {
            this.name = existing.getName();
            this.type = existing.getType();
            this.defaultValue = existing.getDefaultValue();
            this.description = existing.getDescription();
            this.definition = existing.getDefinition();
        }

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

        public GraphQLArgument build() {
            return new GraphQLArgument(name, description, type, defaultValue, definition);
        }
    }


}
