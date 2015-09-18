package graphql.schema;


import static graphql.Assert.assertNotNull;

public class GraphQLScalarType extends AbstractGraphQLType implements GraphQLInputType, GraphQLOutputType, GraphQLUnmodifiedType,GraphQLNullableType {

    private final String description;
    private final Coercing coercing;


    public GraphQLScalarType(String name, String description, Coercing coercing) {
        super(name);
        assertNotNull(coercing, "coercing can't be null");
        this.description = description;
        this.coercing = coercing;
    }


    public String getDescription() {
        return description;
    }


    public Coercing getCoercing() {
        return coercing;
    }

    @Override
    public String toString() {
        return "GraphQLScalarType{" +
                "name='" + getName() + '\'' +
                ", description='" + description + '\'' +
                ", coercing=" + coercing +
                '}';
    }
}
