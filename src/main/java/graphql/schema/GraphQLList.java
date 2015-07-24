package graphql.schema;


import java.util.Map;

import static graphql.Assert.assertNotNull;

public class GraphQLList implements GraphQLType, GraphQLInputType, GraphQLOutputType, GraphQLModifiedType, GraphQLNullableType {

    private GraphQLType wrappedType;

    public GraphQLList(GraphQLType wrappedType) {
        assertNotNull(wrappedType, "wrappedType can't be null");
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
        return null;
    }
}
