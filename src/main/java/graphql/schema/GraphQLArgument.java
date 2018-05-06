package graphql.schema;


import graphql.PublicApi;
import graphql.language.InputValueDefinition;
import graphql.util.FpKit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static graphql.util.FpKit.valuesToList;

/**
 * This defines an argument that can be supplied to a graphql field (via {@link graphql.schema.GraphQLFieldDefinition}.
 *
 * Fields can be thought of as "functions" that take arguments and return a value.
 *
 * See http://graphql.org/learn/queries/#arguments for more details on the concept.
 */
@PublicApi
public class GraphQLArgument implements GraphQLDirectiveContainer {

    private final String name;
    private final String description;
    private GraphQLInputType type;
    private final Object defaultValue;
    private final InputValueDefinition definition;
    private final List<GraphQLDirective> directives;

    public GraphQLArgument(String name, String description, GraphQLInputType type, Object defaultValue) {
        this(name, description, type, defaultValue, null);
    }

    public GraphQLArgument(String name, GraphQLInputType type) {
        this(name, null, type, null, null);
    }

    public GraphQLArgument(String name, String description, GraphQLInputType type, Object defaultValue, InputValueDefinition definition) {
        this(name, description, type, defaultValue, definition, Collections.emptyList());
    }

    private GraphQLArgument(String name, String description, GraphQLInputType type, Object defaultValue, InputValueDefinition definition, List<GraphQLDirective> directives) {
        assertValidName(name);
        assertNotNull(type, "type can't be null");
        this.name = name;
        this.description = description;
        this.type = type;
        this.defaultValue = defaultValue;
        this.definition = definition;
        this.directives = directives;
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

    @Override
    public List<GraphQLDirective> getDirectives() {
        return new ArrayList<>(directives);
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
        private final Map<String, GraphQLDirective> directives = new LinkedHashMap<>();

        public Builder() {
        }

        public Builder(GraphQLArgument existing) {
            this.name = existing.getName();
            this.type = existing.getType();
            this.defaultValue = existing.getDefaultValue();
            this.description = existing.getDescription();
            this.definition = existing.getDefinition();
            this.directives.putAll(FpKit.getByName(existing.getDirectives(), GraphQLDirective::getName));
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

        public Builder withDirectives(GraphQLDirective... directives) {
            assertNotNull(directives, "directives can't be null");
            for (GraphQLDirective directive : directives) {
                withDirective(directive);
            }
            return this;
        }

        public Builder withDirective(GraphQLDirective directive) {
            assertNotNull(directive, "directive can't be null");
            directives.put(directive.getName(), directive);
            return this;
        }

        public Builder withDirective(GraphQLDirective.Builder builder) {
            return withDirective(builder.build());
        }

        /**
         * This is used to clear all the directives in the builder so far.
         *
         * @return the builder
         */
        public Builder clearDirectives() {
            directives.clear();
            return this;
        }


        public GraphQLArgument build() {
            return new GraphQLArgument(name, description, type, defaultValue, definition, valuesToList(directives));
        }
    }


}
