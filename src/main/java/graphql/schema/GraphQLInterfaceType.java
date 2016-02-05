package graphql.schema;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;

public class GraphQLInterfaceType implements GraphQLType, GraphQLOutputType, GraphQLFieldsContainer, GraphQLCompositeType, GraphQLUnmodifiedType, GraphQLNullableType {

    private final String name;
    private final String description;
    private final Map<String, GraphQLFieldDefinition> fieldDefinitionsByName = new LinkedHashMap<String, GraphQLFieldDefinition>();
    private final TypeResolver typeResolver;

    public GraphQLInterfaceType(String name, String description, List<GraphQLFieldDefinition> fieldDefinitions, TypeResolver typeResolver) {
        assertNotNull(name, "name can't null");
        assertNotNull(typeResolver, "typeResolver can't null");
        assertNotNull(fieldDefinitions, "fieldDefinitions can't null");
        this.name = name;
        this.description = description;
        buildDefinitionMap(fieldDefinitions);
        this.typeResolver = typeResolver;
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
        return new ArrayList<GraphQLFieldDefinition>(fieldDefinitionsByName.values());
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public TypeResolver getTypeResolver() {
        return typeResolver;
    }

    @Override
    public String toString() {
        return "GraphQLInterfaceType{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", fieldDefinitionsByName=" + fieldDefinitionsByName +
                ", typeResolver=" + typeResolver +
                '}';
    }

    public static Builder newInterface() {
        return new Builder();
    }


    public static class Builder {
        private String name;
        private String description;
        private List<GraphQLFieldDefinition> fields = new ArrayList<GraphQLFieldDefinition>();
        private TypeResolver typeResolver;


        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder field(GraphQLFieldDefinition fieldDefinition) {
            fields.add(fieldDefinition);
            return this;
        }

        public Builder fields(List<GraphQLFieldDefinition> fieldDefinitions) {
            assertNotNull(fieldDefinitions, "fieldDefinitions can't be null");
            fields.addAll(fieldDefinitions);
            return this;
        }

        public Builder typeResolver(TypeResolver typeResolver) {
            this.typeResolver = typeResolver;
            return this;
        }

        public GraphQLInterfaceType build() {
            return new GraphQLInterfaceType(name, description, fields, typeResolver);
        }


    }


}
