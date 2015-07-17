package graphql.schema;


import java.util.Map;

public class GraphQLList implements GraphQLType, GraphQLInputType, GraphQLOutputType, GraphQLModifiedType, GraphQLNullableType {

    private  GraphQLType wrappedType;

    public GraphQLList(GraphQLType wrappedType) {
        this.wrappedType = wrappedType;
    }


    public GraphQLType getWrappedType() {
        return wrappedType;
    }

    void replaceTypeReferences(Map<String, GraphQLType> typeMap) {
        wrappedType = new SchemaUtil().resolveTypeReference(wrappedType, typeMap);
    }

    @Override
    public String getName() {
        return "GraphQLList";
    }
}
