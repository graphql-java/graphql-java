package graphql.schema;


import java.util.List;

public class GraphQLInterfaceType implements GraphQLType,GraphQLOutputType{

    String name;


    public List<GraphQLFieldDefinition> getFields(){
        return null;
    }
}
