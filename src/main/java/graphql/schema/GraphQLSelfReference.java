package graphql.schema;


public class GraphQLSelfReference implements GraphQLType {

    private final String name;

    public GraphQLSelfReference(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
