package graphql.schema;


import java.util.ArrayList;
import java.util.List;

public class GraphQLUnionType implements GraphQLType, GraphQLOutputType {

    private final List<GraphQLType> types = new ArrayList<>();

    public GraphQLUnionType(List<GraphQLType> possibleTypes) {
        this.types.addAll(possibleTypes);
    }

    public List<GraphQLType> getTypes() {
        return types;
    }
}
