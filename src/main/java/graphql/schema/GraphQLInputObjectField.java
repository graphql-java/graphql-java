package graphql.schema;


import graphql.Internal;
import graphql.PublicApi;
import graphql.language.InputValueDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static java.util.Collections.emptyList;

/**
 * Input objects defined via {@link graphql.schema.GraphQLInputObjectType} contains these input fields.
 *
 * There are similar to {@link graphql.schema.GraphQLFieldDefinition} however they can ONLY be used on input objects, that
 * is to describe values that are fed into a graphql mutation.
 *
 * See http://graphql.org/learn/schema/#input-types for more details on the concept.
 */
@PublicApi
public class GraphQLInputObjectField implements GraphQLDirectiveContainer {

    private final String name;
    private final String description;
    private GraphQLInputType type;
    private final Object defaultValue;
    private final InputValueDefinition definition;
    private final List<GraphQLDirective> directives;

    @Internal
    public GraphQLInputObjectField(String name, GraphQLInputType type) {
        this(name, null, type, null, emptyList(), null);
    }

    @Internal
    public GraphQLInputObjectField(String name, String description, GraphQLInputType type, Object defaultValue) {
        this(name, description, type, defaultValue, emptyList(), null);
    }

    @Internal
    public GraphQLInputObjectField(String name, String description, GraphQLInputType type, Object defaultValue, List<GraphQLDirective> directives, InputValueDefinition definition) {
        assertValidName(name);
        assertNotNull(type, "type can't be null");
        assertNotNull(directives, "directives cannot be null");

        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.description = description;
        this.directives = directives;
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

    @Override
    public List<GraphQLDirective> getDirectives() {
        return new ArrayList<>(directives);
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
        private final List<GraphQLDirective> directives = new ArrayList<>();

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

        public Builder withDirectives(GraphQLDirective... directives) {
            Collections.addAll(this.directives, directives);
            return this;
        }

        public GraphQLInputObjectField build() {
            return new GraphQLInputObjectField(name, description, type, defaultValue, directives, definition);
        }
    }
}