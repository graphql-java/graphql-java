package graphql.schema;


import java.util.Map;

public class GraphQLNonNull implements GraphQLType, GraphQLInputType, GraphQLOutputType, GraphQLModifiedType {

    private  GraphQLType wrappedType;

    public GraphQLNonNull(GraphQLType wrappedType) {
        this.wrappedType = wrappedType;
    }

    public GraphQLType getWrappedType() {
        return wrappedType;
    }

    void replaceTypeReferences(Map<String, GraphQLType> typeMap) {
        wrappedType = SchemaUtil.resolveTypeReference(wrappedType, typeMap);
    }


    @Override
    public String toString() {
        return "GraphQLNonNull{" +
                "wrappedType=" + wrappedType +
                '}';
    }

    @Override
    public String getName() {
        return "GraphQLNonNull";
    }
}
