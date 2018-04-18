package graphql.schema2;


import graphql.PublicApi;
import graphql.language.InputValueDefinition;

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
    private TypeReference type;
    private final Object defaultValue;
    private final InputValueDefinition definition;


    public GraphQLArgument(String name, String description, TypeReference type, Object defaultValue, InputValueDefinition definition) {
        assertValidName(name);
        assertNotNull(type, "type can't be null");
        this.name = name;
        this.description = description;
        this.type = type;
        this.defaultValue = defaultValue;
        this.definition = definition;
    }


    public String getName() {
        return name;
    }

    public TypeReference getType() {
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
