package graphql.schema2;

import graphql.AssertException;
import graphql.Internal;
import graphql.PublicApi;
import graphql.language.ObjectTypeDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static java.lang.String.format;

/**
 * This is the work horse type and represents an object with one or more field values that can be retrieved
 * by the graphql system.
 *
 * Those fields can themselves by object types and so on until you reach the leaf nodes of the type tree represented
 * by {@link graphql.schema.GraphQLScalarType}s.
 *
 * See http://graphql.org/learn/schema/#object-types-and-fields for more details on the concept.
 */
@PublicApi
public class GraphQLObjectType implements GraphQLType, GraphQLOutputType, GraphQLFieldsContainer, GraphQLCompositeType, GraphQLUnmodifiedType, GraphQLNullableType {


    private final String name;
    private final String description;
    private final Map<String, GraphQLFieldDefinition> fieldDefinitionsByName = new LinkedHashMap<>();
    private List<TypeReference> interfaces = new ArrayList<>();
    private final ObjectTypeDefinition definition;


    @Internal
    public GraphQLObjectType(String name, String description, List<GraphQLFieldDefinition> fieldDefinitions,
                             List<TypeReference> interfaces, ObjectTypeDefinition definition) {
        assertValidName(name);
        assertNotNull(fieldDefinitions, "fieldDefinitions can't be null");
        assertNotNull(interfaces, "interfaces can't be null");
        this.name = name;
        this.description = description;
        this.interfaces = interfaces;
        this.definition = definition;
        buildDefinitionMap(fieldDefinitions);
    }

    private void buildDefinitionMap(List<GraphQLFieldDefinition> fieldDefinitions) {
        for (GraphQLFieldDefinition fieldDefinition : fieldDefinitions) {
            String name = fieldDefinition.getName();
            if (fieldDefinitionsByName.containsKey(name))
                throw new AssertException(format("Duplicated definition for field '%s' in type '%s'", name, this.name));
            fieldDefinitionsByName.put(name, fieldDefinition);
        }
    }

    public GraphQLFieldDefinition getFieldDefinition(String name) {
        return fieldDefinitionsByName.get(name);
    }


    public List<GraphQLFieldDefinition> getFieldDefinitions() {
        return new ArrayList<>(fieldDefinitionsByName.values());
    }


    /**
     * @return This returns GraphQLInterface or GraphQLTypeReference instances, if the type
     * references are not resolved yet. After they are resolved it contains only GraphQLInterface.
     * Reference resolving happens when a full schema is built.
     */
    public List<TypeReference> getInterfaces() {
        return new ArrayList<>(interfaces);
    }

    public String getDescription() {
        return description;
    }


    public String getName() {
        return name;
    }

    public ObjectTypeDefinition getDefinition() {
        return definition;
    }

    @Override
    public String toString() {
        return "GraphQLObjectType{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", fieldDefinitionsByName=" + fieldDefinitionsByName.keySet() +
                ", interfaces=" + interfaces +
                '}';
    }

}
