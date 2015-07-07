package graphql.schema;


public class GraphQLNonNull implements GraphQLType, GraphQLInputType, GraphQLOutputType {

    private GraphQLType wrappedType;

    public GraphQLType getWrappedType() {
        return wrappedType;
    }

    public void setWrappedType(GraphQLType wrappedType) {
        this.wrappedType = wrappedType;
    }
}
