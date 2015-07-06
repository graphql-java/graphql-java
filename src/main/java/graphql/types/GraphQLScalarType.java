package graphql.types;

public class GraphQLScalarType {

    private final String name;
    private final String description;

    public GraphQLScalarType(String name, String description) {
        this.name = name;
        this.description = description;
    }

}
