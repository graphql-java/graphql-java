package graphql.schema;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.Assert;
import graphql.DirectivesUtil;
import graphql.Internal;
import graphql.PublicApi;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.util.FpKit;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Assert.assertValidName;
import static graphql.schema.GraphqlTypeComparators.sortTypes;
import static graphql.util.FpKit.getByName;
import static graphql.util.FpKit.valuesToList;
import static java.util.Collections.emptyList;

/**
 * This is the work horse type and represents an object with one or more field values that can be retrieved
 * by the graphql system.
 * <p>
 * Those fields can themselves by object types and so on until you reach the leaf nodes of the type tree represented
 * by {@link graphql.schema.GraphQLScalarType}s.
 * <p>
 * See http://graphql.org/learn/schema/#object-types-and-fields for more details on the concept.
 */
@PublicApi
public class GraphQLObjectType implements GraphQLNamedOutputType, GraphQLCompositeType, GraphQLUnmodifiedType, GraphQLNullableType, GraphQLDirectiveContainer, GraphQLImplementingType {


    private final String name;
    private final String description;
    private final Comparator<? super GraphQLSchemaElement> interfaceComparator;
    private final ImmutableMap<String, GraphQLFieldDefinition> fieldDefinitionsByName;
    private final ImmutableList<GraphQLNamedOutputType> originalInterfaces;
    private final DirectivesUtil.DirectivesHolder directivesHolder;
    private final ObjectTypeDefinition definition;
    private final ImmutableList<ObjectTypeExtensionDefinition> extensionDefinitions;

    private ImmutableList<GraphQLNamedOutputType> replacedInterfaces;

    public static final String CHILD_INTERFACES = "interfaces";
    public static final String CHILD_FIELD_DEFINITIONS = "fieldDefinitions";

    @Internal
    private GraphQLObjectType(String name,
                              String description,
                              List<GraphQLFieldDefinition> fieldDefinitions,
                              List<GraphQLNamedOutputType> interfaces,
                              List<GraphQLDirective> directives,
                              List<GraphQLAppliedDirective> appliedDirectives,
                              ObjectTypeDefinition definition,
                              List<ObjectTypeExtensionDefinition> extensionDefinitions,
                              Comparator<? super GraphQLSchemaElement> interfaceComparator) {
        assertValidName(name);
        assertNotNull(fieldDefinitions, () -> "fieldDefinitions can't be null");
        assertNotNull(interfaces, () -> "interfaces can't be null");
        this.name = name;
        this.description = description;
        this.interfaceComparator = interfaceComparator;
        this.originalInterfaces = ImmutableList.copyOf(sortTypes(interfaceComparator, interfaces));
        this.definition = definition;
        this.extensionDefinitions = ImmutableList.copyOf(extensionDefinitions);
        this.directivesHolder = new DirectivesUtil.DirectivesHolder(directives, appliedDirectives);
        this.fieldDefinitionsByName = buildDefinitionMap(fieldDefinitions);
    }

    void replaceInterfaces(List<GraphQLNamedOutputType> interfaces) {
        this.replacedInterfaces = ImmutableList.copyOf(sortTypes(interfaceComparator, interfaces));
    }

    private ImmutableMap<String, GraphQLFieldDefinition> buildDefinitionMap(List<GraphQLFieldDefinition> fieldDefinitions) {
        return ImmutableMap.copyOf(FpKit.getByName(fieldDefinitions, GraphQLFieldDefinition::getName,
                (fld1, fld2) -> assertShouldNeverHappen("Duplicated definition for field '%s' in type '%s'", fld1.getName(), this.name)));
    }

    @Override
    public List<GraphQLDirective> getDirectives() {
        return directivesHolder.getDirectives();
    }

    @Override
    public Map<String, GraphQLDirective> getDirectivesByName() {
        return directivesHolder.getDirectivesByName();
    }

    @Override
    public Map<String, List<GraphQLDirective>> getAllDirectivesByName() {
        return directivesHolder.getAllDirectivesByName();
    }

    @Override
    public GraphQLDirective getDirective(String directiveName) {
        return directivesHolder.getDirective(directiveName);
    }

    @Override
    public List<GraphQLAppliedDirective> getAppliedDirectives() {
        return directivesHolder.getAppliedDirectives();
    }

    @Override
    public Map<String, List<GraphQLAppliedDirective>> getAllAppliedDirectivesByName() {
        return directivesHolder.getAllAppliedDirectivesByName();
    }

    @Override
    public GraphQLAppliedDirective getAppliedDirective(String directiveName) {
        return directivesHolder.getAppliedDirective(directiveName);
    }

    @Override
    public GraphQLFieldDefinition getFieldDefinition(String name) {
        return fieldDefinitionsByName.get(name);
    }

    @Override
    public List<GraphQLFieldDefinition> getFieldDefinitions() {
        return ImmutableList.copyOf(fieldDefinitionsByName.values());
    }


    @Override
    public List<GraphQLNamedOutputType> getInterfaces() {
        if (replacedInterfaces != null) {
            return replacedInterfaces;
        }
        return originalInterfaces;
    }

    public String getDescription() {
        return description;
    }


    @Override
    public String getName() {
        return name;
    }

    public ObjectTypeDefinition getDefinition() {
        return definition;
    }

    public List<ObjectTypeExtensionDefinition> getExtensionDefinitions() {
        return extensionDefinitions;
    }

    @Override
    public String toString() {
        return "GraphQLObjectType{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", fieldDefinitionsByName=" + fieldDefinitionsByName.keySet() +
                ", interfaces=" + getInterfaces() +
                '}';
    }

    /**
     * This helps you transform the current GraphQLObjectType into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new object based on calling build on that builder
     */
    public GraphQLObjectType transform(Consumer<Builder> builderConsumer) {
        Builder builder = newObject(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @Override
    public GraphQLSchemaElement copy() {
        return newObject(this).build();
    }

    @Override
    public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
        return visitor.visitGraphQLObjectType(this, context);
    }

    @Override
    public List<GraphQLSchemaElement> getChildren() {
        List<GraphQLSchemaElement> children = new ArrayList<>(fieldDefinitionsByName.values());
        children.addAll(getInterfaces());
        children.addAll(directivesHolder.getDirectives());
        children.addAll(directivesHolder.getAppliedDirectives());
        return children;
    }

    @Override
    public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
        return SchemaElementChildrenContainer.newSchemaElementChildrenContainer()
                .children(CHILD_FIELD_DEFINITIONS, fieldDefinitionsByName.values())
                .children(CHILD_INTERFACES, originalInterfaces)
                .children(CHILD_APPLIED_DIRECTIVES, directivesHolder.getAppliedDirectives())
                .children(CHILD_DIRECTIVES, directivesHolder.getDirectives())
                .build();
    }

    // Spock mocking fails with the real return type GraphQLObjectType
    @Override
    public GraphQLSchemaElement withNewChildren(SchemaElementChildrenContainer newChildren) {
        return transform(builder ->
                builder
                        .replaceFields(newChildren.getChildren(CHILD_FIELD_DEFINITIONS))
                        .replaceInterfaces(newChildren.getChildren(CHILD_INTERFACES))
                        .replaceDirectives(newChildren.getChildren(CHILD_DIRECTIVES))
                        .replaceAppliedDirectives(newChildren.getChildren(CHILD_APPLIED_DIRECTIVES))
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean equals(Object o) {
        return super.equals(o);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int hashCode() {
        return super.hashCode();
    }


    public static Builder newObject() {
        return new Builder();
    }

    public static Builder newObject(GraphQLObjectType existing) {
        return new Builder(existing);
    }

    @PublicApi
    public static class Builder extends GraphqlDirectivesContainerTypeBuilder<Builder,Builder> {
        private ObjectTypeDefinition definition;
        private List<ObjectTypeExtensionDefinition> extensionDefinitions = emptyList();
        private final Map<String, GraphQLFieldDefinition> fields = new LinkedHashMap<>();
        private final Map<String, GraphQLNamedOutputType> interfaces = new LinkedHashMap<>();

        public Builder() {
        }

        public Builder(GraphQLObjectType existing) {
            name = existing.getName();
            description = existing.getDescription();
            definition = existing.getDefinition();
            extensionDefinitions = existing.getExtensionDefinitions();
            fields.putAll(getByName(existing.getFieldDefinitions(), GraphQLFieldDefinition::getName));
            interfaces.putAll(getByName(existing.originalInterfaces, GraphQLNamedType::getName));
            copyExistingDirectives(existing);
        }

        public Builder definition(ObjectTypeDefinition definition) {
            this.definition = definition;
            return this;
        }

        public Builder extensionDefinitions(List<ObjectTypeExtensionDefinition> extensionDefinitions) {
            this.extensionDefinitions = extensionDefinitions;
            return this;
        }

        public Builder field(GraphQLFieldDefinition fieldDefinition) {
            assertNotNull(fieldDefinition, () -> "fieldDefinition can't be null");
            this.fields.put(fieldDefinition.getName(), fieldDefinition);
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
            assertNotNull(builderFunction, () -> "builderFunction can't be null");
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
            return field(builder.build());
        }

        public Builder fields(List<GraphQLFieldDefinition> fieldDefinitions) {
            assertNotNull(fieldDefinitions, () -> "fieldDefinitions can't be null");
            fieldDefinitions.forEach(this::field);
            return this;
        }

        public Builder replaceFields(List<GraphQLFieldDefinition> fieldDefinitions) {
            assertNotNull(fieldDefinitions, () -> "fieldDefinitions can't be null");
            this.fields.clear();
            fieldDefinitions.forEach(this::field);
            return this;
        }

        /**
         * This is used to clear all the fields in the builder so far.
         *
         * @return the builder
         */
        public Builder clearFields() {
            fields.clear();
            return this;
        }

        public boolean hasField(String fieldName) {
            return fields.containsKey(fieldName);
        }


        public Builder withInterface(GraphQLInterfaceType interfaceType) {
            assertNotNull(interfaceType, () -> "interfaceType can't be null");
            this.interfaces.put(interfaceType.getName(), interfaceType);
            return this;
        }

        public Builder replaceInterfaces(List<? extends GraphQLNamedOutputType> interfaces) {
            assertNotNull(interfaces, () -> "interfaces can't be null");
            this.interfaces.clear();
            for (GraphQLNamedOutputType schemaElement : interfaces) {
                if (schemaElement instanceof GraphQLInterfaceType || schemaElement instanceof GraphQLTypeReference) {
                    this.interfaces.put(schemaElement.getName(), schemaElement);
                } else {
                    Assert.assertShouldNeverHappen("Unexpected type " + (schemaElement != null ? schemaElement.getClass() : "null"));
                }
            }
            return this;
        }

        public Builder withInterface(GraphQLTypeReference reference) {
            assertNotNull(reference, () -> "reference can't be null");
            this.interfaces.put(reference.getName(), reference);
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

        /**
         * This is used to clear all the interfaces in the builder so far.
         *
         * @return the builder
         */
        public Builder clearInterfaces() {
            interfaces.clear();
            return this;
        }

        // -- the following are repeated to avoid a binary incompatibility problem --

        @Override
        public Builder replaceDirectives(List<GraphQLDirective> directives) {
            return super.replaceDirectives(directives);
        }

        @Override
        public Builder withDirectives(GraphQLDirective... directives) {
            return super.withDirectives(directives);
        }

        @Override
        public Builder withDirective(GraphQLDirective directive) {
            return super.withDirective(directive);
        }

        @Override
        public Builder withDirective(GraphQLDirective.Builder builder) {
            return super.withDirective(builder);
        }

        @Override
        public Builder clearDirectives() {
            return super.clearDirectives();
        }

        @Override
        public Builder name(String name) {
            return super.name(name);
        }

        @Override
        public Builder description(String description) {
            return super.description(description);
        }

        public GraphQLObjectType build() {
            return new GraphQLObjectType(
                    name,
                    description,
                    sort(fields, GraphQLObjectType.class, GraphQLFieldDefinition.class),
                    valuesToList(interfaces),
                    sort(directives, GraphQLObjectType.class, GraphQLDirective.class),
                    sort(appliedDirectives, GraphQLObjectType.class, GraphQLAppliedDirective.class),
                    definition,
                    extensionDefinitions,
                    getComparator(GraphQLObjectType.class, GraphQLInterfaceType.class)
            );
        }
    }
}
