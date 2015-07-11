package graphql.schema;


public class GraphQLTypeReference implements GraphQLType {

    private final String name;

    public GraphQLTypeReference(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
