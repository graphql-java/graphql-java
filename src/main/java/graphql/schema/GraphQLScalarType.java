package graphql.schema;


import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;

public class GraphQLScalarType implements GraphQLType, GraphQLInputType, GraphQLOutputType, GraphQLUnmodifiedType, GraphQLNullableType {

    private final String name;
    private final String description;
    private final Coercing coercing;


    public GraphQLScalarType(String name, String description, Coercing coercing) {
    	assertValidName(name);
        assertNotNull(coercing, "coercing can't be null");
        this.name = name;
        this.description = description;
        this.coercing = coercing;
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

    @Override
    public String toString() {
        return "GraphQLScalarType{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", coercing=" + coercing +
                '}';
    }
}
