package graphql.schema;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;

/**
 * <p>GraphQLObjectType class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class GraphQLObjectType implements GraphQLType, GraphQLOutputType, GraphQLFieldsContainer, GraphQLCompositeType, GraphQLUnmodifiedType, GraphQLNullableType {

    private final String name;
    private final String description;
    private final Map<String, GraphQLFieldDefinition> fieldDefinitionsByName = new LinkedHashMap<String, GraphQLFieldDefinition>();
    private final List<GraphQLInterfaceType> interfaces = new ArrayList<GraphQLInterfaceType>();

    /**
     * <p>Constructor for GraphQLObjectType.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param description a {@link java.lang.String} object.
     * @param fieldDefinitions a {@link java.util.List} object.
     * @param interfaces a {@link java.util.List} object.
     */
    public GraphQLObjectType(String name, String description, List<GraphQLFieldDefinition> fieldDefinitions, List<GraphQLInterfaceType> interfaces) {
        assertNotNull(name, "name can't null");
        assertNotNull(fieldDefinitions, "fieldDefinitions can't null");
        assertNotNull(interfaces, "interfaces can't null");
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


    /** {@inheritDoc} */
    public GraphQLFieldDefinition getFieldDefinition(String name) {
        return fieldDefinitionsByName.get(name);
    }


    /**
     * <p>getFieldDefinitions.</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<GraphQLFieldDefinition> getFieldDefinitions() {
        return new ArrayList<GraphQLFieldDefinition>(fieldDefinitionsByName.values());
    }


    /**
     * <p>Getter for the field <code>interfaces</code>.</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<GraphQLInterfaceType> getInterfaces() {
        return new ArrayList<GraphQLInterfaceType>(interfaces);
    }

    /**
     * <p>Getter for the field <code>description</code>.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getDescription() {
        return description;
    }


    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getName() {
        return name;
    }


    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "GraphQLObjectType{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", fieldDefinitionsByName=" + fieldDefinitionsByName +
                ", interfaces=" + interfaces +
                '}';
    }

    /**
     * <p>newObject.</p>
     *
     * @return a {@link graphql.schema.GraphQLObjectType.Builder} object.
     */
    public static Builder newObject() {
        return new Builder();
    }


    public static class Builder {
        private String name;
        private String description;
        private List<GraphQLFieldDefinition> fieldDefinitions = new ArrayList<GraphQLFieldDefinition>();
        private List<GraphQLInterfaceType> interfaces = new ArrayList<GraphQLInterfaceType>();

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
