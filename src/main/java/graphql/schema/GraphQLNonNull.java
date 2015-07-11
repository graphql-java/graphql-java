package graphql.schema;


public class GraphQLNonNull implements GraphQLType, GraphQLInputType, GraphQLOutputType {

    private final GraphQLType wrappedType;

    public GraphQLNonNull(GraphQLType wrappedType) {
        this.wrappedType = wrappedType;
    }

    public GraphQLType getWrappedType() {
        return wrappedType;
    }

    @Override
    public String toString() {
        return "GraphQLNonNull{" +
                "wrappedType=" + wrappedType +
                '}';
    }
}
