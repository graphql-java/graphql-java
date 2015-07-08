package graphql.schema;


import java.util.*;

public class GraphQLObjectType implements GraphQLType, GraphQLOutputType {

    private final String name;
    private final Map<String, GraphQLFieldDefinition> fieldDefinitionsByName = new LinkedHashMap<>();
    private final List<GraphQLInterfaceType> interfaces = new ArrayList<>();

    public GraphQLObjectType(String name, List<GraphQLFieldDefinition> fieldDefinitions) {
        this(name, fieldDefinitions, Collections.<GraphQLInterfaceType>emptyList());
    }

    public GraphQLObjectType(String name, List<GraphQLFieldDefinition> fieldDefinitions, List<GraphQLInterfaceType> interfaces) {
        this.name = name;
        this.interfaces.addAll(interfaces);
        buildDefinitionMap(fieldDefinitions);
    }

    private void buildDefinitionMap(List<GraphQLFieldDefinition> fieldDefinitions) {
        for (GraphQLFieldDefinition fieldDefinition : fieldDefinitions) {
            fieldDefinitionsByName.put(fieldDefinition.getName(), fieldDefinition);
        }
    }

    public GraphQLFieldDefinition getFieldDefinition(String name) {
        return fieldDefinitionsByName.get(name);
    }


    public List<GraphQLFieldDefinition> getFieldDefinitions() {
        return new ArrayList<>(fieldDefinitionsByName.values());
    }

    public List<Class> getInterfaces() {
        return null;
    }

    public String getName() {
        return name;
    }
}
