package graphql.schema;


public class GraphQLList implements GraphQLType, GraphQLInputType, GraphQLOutputType, GraphQLModifiedType, GraphQLNullableType {

    private final GraphQLType wrappedType;

    public GraphQLList(GraphQLType wrappedType) {
        this.wrappedType = wrappedType;
    }


    public GraphQLType getWrappedType() {
        return wrappedType;
    }

    @Override
    public String getName() {
        return "GraphQLList";
    }
}
