package graphql.schema;


import graphql.language.ScalarTypeDefinition;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;

public class GraphQLScalarType implements GraphQLType, GraphQLInputType, GraphQLOutputType, GraphQLUnmodifiedType, GraphQLNullableType {

    private final String name;
    private final String description;
    private final Coercing coercing;
    private final ScalarTypeDefinition definition;

    public GraphQLScalarType(String name, String description, Coercing coercing) {
        this(name,description,coercing,null);
    }

    public GraphQLScalarType(String name, String description, Coercing coercing, ScalarTypeDefinition definition) {
        assertValidName(name);
        assertNotNull(coercing, "coercing can't be null");
        this.name = name;
        this.description = description;
        this.coercing = coercing;
        this.definition = definition;
    }

    public String getName() {
        return name;
    }


    public String getDescription() {
        return description;
    }


    public Coercing getCoercing() {
        return coercing;
    }

    public ScalarTypeDefinition getDefinition() {
        return definition;
    }

    @Override
    public String toString() {
        return "GraphQLScalarType{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", coercing=" + coercing +
                '}';
    }
}
