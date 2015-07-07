package graphql.schema;


import java.util.List;
import java.util.Map;

public class GraphQLObjectType implements GraphQLType,GraphQLOutputType{

    String name;

    private Map<String,GraphQLFieldDefinition> fieldDefinitions;

    public GraphQLObjectType(String name, Map<String, GraphQLFieldDefinition> fieldDefinitions) {
        this.name = name;
        this.fieldDefinitions = fieldDefinitions;
    }

    public GraphQLFieldDefinition getFieldDefinition(String name){
        return fieldDefinitions.get(name);
    }


    public List<GraphQLFieldDefinition> getFieldDefinitions() {
        return null;
    }

    public List<Class> getInterfaces(){
        return null;
    }
}
