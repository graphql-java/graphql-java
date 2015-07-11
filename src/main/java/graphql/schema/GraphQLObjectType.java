package graphql.schema;


import java.util.*;

public class GraphQLObjectType implements GraphQLType, GraphQLOutputType {

    private final String name;
    private final String description;
    private final Map<String, GraphQLFieldDefinition> fieldDefinitionsByName = new LinkedHashMap<>();
    private final List<GraphQLInterfaceType> interfaces = new ArrayList<>();

    public GraphQLObjectType(String name, String description, List<GraphQLFieldDefinition> fieldDefinitions, List<GraphQLInterfaceType> interfaces) {
        this.name = name;
        this.description = description;
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


    public List<GraphQLInterfaceType> getInterfaces() {
        return interfaces;
    }

    public String getDescription() {
        return description;
    }


    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "GraphQLObjectType{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", fieldDefinitionsByName=" + fieldDefinitionsByName +
                ", interfaces=" + interfaces +
                '}';
    }

    public static Builder newObject() {
        return new Builder();
    }


    public static class Builder {
        private String name;
        private String description;
        private List<GraphQLFieldDefinition> fieldDefinitions = new ArrayList<>();
        private List<GraphQLInterfaceType> interfaces = new ArrayList<>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder field(GraphQLFieldDefinition fieldDefinition) {
            this.fieldDefinitions.add(fieldDefinition);
            return this;
        }

        public Builder withInterface(GraphQLInterfaceType interfaceType) {
            this.interfaces.add(interfaceType);
            return this;
        }

        public GraphQLObjectType build() {
            return new GraphQLObjectType(name, description, fieldDefinitions, interfaces);
        }


    }
}
