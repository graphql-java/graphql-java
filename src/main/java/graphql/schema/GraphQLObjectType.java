package graphql.schema;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;

public class GraphQLObjectType extends AbstractGraphQLType implements GraphQLOutputType, GraphQLFieldsContainer, GraphQLCompositeType, GraphQLUnmodifiedType, GraphQLNullableType {

    private final String description;
    private final Map<String, GraphQLFieldDefinition> fieldDefinitionsByName = new LinkedHashMap<>();
    private final List<GraphQLInterfaceType> interfaces = new ArrayList<>();

    public GraphQLObjectType(String name, String description, List<GraphQLFieldDefinition> fieldDefinitions, List<GraphQLInterfaceType> interfaces) {
        super(name);
        assertNotNull(fieldDefinitions, "fieldDefinitions can't null");
        assertNotNull(interfaces, "interfaces can't null");
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
        return new ArrayList<>(interfaces);
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "GraphQLObjectType{" +
                "name='" + getName() + '\'' +
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
            assertNotNull(fieldDefinition, "fieldDefinition can't be null");
            this.fieldDefinitions.add(fieldDefinition);
            return this;
        }

        public Builder fields(List<GraphQLFieldDefinition> fieldDefinitions) {
            assertNotNull(fieldDefinitions, "fieldDefinitions can't be null");
            this.fieldDefinitions.addAll(fieldDefinitions);
            return this;
        }

        public Builder withInterface(GraphQLInterfaceType interfaceType) {
            assertNotNull(interfaceType, "interfaceType can't be null");
            this.interfaces.add(interfaceType);
            return this;
        }

        public Builder withInterfaces(GraphQLInterfaceType... interfaceType) {
            for (GraphQLInterfaceType type : interfaceType) {
                withInterface(type);
            }
            return this;
        }

        public GraphQLObjectType build() {
            return new GraphQLObjectType(name, description, fieldDefinitions, interfaces);
        }


    }
}
