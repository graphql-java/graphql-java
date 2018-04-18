package graphql.schema2;


import graphql.Internal;
import graphql.PublicApi;
import graphql.language.InputValueDefinition;
import graphql.schema.GraphQLInputType;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;

/**
 * Input objects defined via {@link GraphQLInputObjectType} contains these input fields.
 *
 * There are similar to {@link graphql.schema.GraphQLFieldDefinition} however they can ONLY be used on input objects, that
 * is to describe values that are fed into a graphql mutation.
 *
 * See http://graphql.org/learn/schema/#input-types for more details on the concept.
 */
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
        this(name, description, type, defaultValue, null);
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

}