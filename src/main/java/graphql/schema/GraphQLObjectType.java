package graphql.schema;

import graphql.AssertException;
import graphql.Internal;
import graphql.PublicApi;
import graphql.language.ObjectTypeDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;

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
public class GraphQLObjectType implements GraphQLType, GraphQLOutputType, GraphQLFieldsContainer, GraphQLCompositeType, GraphQLUnmodifiedType, GraphQLNullableType, GraphQLMetadataSupport {


    private final String name;
    private final String description;
    private final Map<String, GraphQLFieldDefinition> fieldDefinitionsByName = new LinkedHashMap<>();
    private List<GraphQLOutputType> interfaces = new ArrayList<>();
    private final ObjectTypeDefinition definition;
    private final Map<String, Object> metadata;

    @Internal
    public GraphQLObjectType(String name, String description, List<GraphQLFieldDefinition> fieldDefinitions,
                             List<GraphQLOutputType> interfaces) {
        this(name, description, fieldDefinitions, interfaces, null, null);
    }

    @Internal
    public GraphQLObjectType(String name, String description, List<GraphQLFieldDefinition> fieldDefinitions,
                             List<GraphQLOutputType> interfaces, ObjectTypeDefinition definition, Map<String, Object> metadata) {
        assertValidName(name);
        assertNotNull(fieldDefinitions, "fieldDefinitions can't be null");
        assertNotNull(interfaces, "interfaces can't be null");
        this.name = name;
        this.description = description;
        this.interfaces = interfaces;
        this.definition = definition;
        this.metadata = metadata == null ? emptyMap() : unmodifiableMap(metadata);
        buildDefinitionMap(fieldDefinitions);
    }

    void replaceTypeReferences(Map<String, GraphQLType> typeMap) {
        this.interfaces = this.interfaces.stream()
                .map(type -> (GraphQLOutputType) new SchemaUtil().resolveTypeReference(type, typeMap))
                .collect(Collectors.toList());
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
    public List<GraphQLOutputType> getInterfaces() {
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
    public Map<String, Object> getMetadata() {
        return metadata;
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

    public static Builder newObject() {
        return new Builder();
    }

    @PublicApi
    public static class Builder {
        private String name;
        private String description;
        private List<GraphQLFieldDefinition> fieldDefinitions = new ArrayList<>();
        private List<GraphQLOutputType> interfaces = new ArrayList<>();
        private ObjectTypeDefinition definition;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder definition(ObjectTypeDefinition definition) {
            this.definition = definition;
            return this;
        }

        public Builder field(GraphQLFieldDefinition fieldDefinition) {
            assertNotNull(fieldDefinition, "fieldDefinition can't be null");
            this.fieldDefinitions.add(fieldDefinition);
            return this;
        }

        /**
         * Take a field builder in a function definition and apply. Can be used in a jdk8 lambda
         * e.g.:
         * <pre>
         *     {@code
         *      field(f -> f.name("fieldName"))
         *     }
         * </pre>
         *
         * @param builderFunction a supplier for the builder impl
         *
         * @return this
         */
        public Builder field(UnaryOperator<GraphQLFieldDefinition.Builder> builderFunction) {
            assertNotNull(builderFunction, "builderFunction can't be null");
            GraphQLFieldDefinition.Builder builder = GraphQLFieldDefinition.newFieldDefinition();
            builder = builderFunction.apply(builder);
            return field(builder.build());
        }

        /**
         * Same effect as the field(GraphQLFieldDefinition). Builder.build() is called
         * from within
         *
         * @param builder an un-built/incomplete GraphQLFieldDefinition
         *
         * @return this
         */
        public Builder field(GraphQLFieldDefinition.Builder builder) {
            this.fieldDefinitions.add(builder.build());
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

        public Builder withInterface(GraphQLTypeReference reference) {
            assertNotNull(reference, "reference can't be null");
            this.interfaces.add(reference);
            return this;
        }

        public Builder withInterfaces(GraphQLInterfaceType... interfaceType) {
            for (GraphQLInterfaceType type : interfaceType) {
                withInterface(type);
            }
            return this;
        }

        public Builder withInterfaces(GraphQLTypeReference... references) {
            for (GraphQLTypeReference reference : references) {
                withInterface(reference);
            }
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata == null ? new HashMap<>() : metadata;
            return this;
        }

        public Builder metadata(String key, Object metadataValue) {
            this.metadata.put(key, metadataValue);
            return this;
        }

        public GraphQLObjectType build() {
            return new GraphQLObjectType(name, description, fieldDefinitions, interfaces, definition, metadata);
        }

    }

}
