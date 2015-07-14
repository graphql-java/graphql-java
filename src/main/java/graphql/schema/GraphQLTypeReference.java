package graphql.schema;


public class GraphQLTypeReference implements GraphQLType, GraphQLOutputType {

    private final String name;

    public GraphQLTypeReference(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
