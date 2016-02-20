package graphql.schema;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;

/**
 * <p>GraphQLInterfaceType class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class GraphQLInterfaceType implements GraphQLType, GraphQLOutputType, GraphQLFieldsContainer, GraphQLCompositeType, GraphQLUnmodifiedType, GraphQLNullableType {

    private final String name;
    private final String description;
    private final Map<String, GraphQLFieldDefinition> fieldDefinitionsByName = new LinkedHashMap<String, GraphQLFieldDefinition>();
    private final TypeResolver typeResolver;

    /**
     * <p>Constructor for GraphQLInterfaceType.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param description a {@link java.lang.String} object.
     * @param fieldDefinitions a {@link java.util.List} object.
     * @param typeResolver a {@link graphql.schema.TypeResolver} object.
     */
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
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getName() {
        return name;
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
     * <p>Getter for the field <code>typeResolver</code>.</p>
     *
     * @return a {@link graphql.schema.TypeResolver} object.
     */
    public TypeResolver getTypeResolver() {
        return typeResolver;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "GraphQLInterfaceType{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", fieldDefinitionsByName=" + fieldDefinitionsByName +
                ", typeResolver=" + typeResolver +
                '}';
    }

    /**
     * <p>newInterface.</p>
     *
     * @return a {@link graphql.schema.GraphQLInterfaceType.Builder} object.
     */
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
