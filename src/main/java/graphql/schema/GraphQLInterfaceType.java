package graphql.schema;

import com.google.common.collect.ImmutableList;
import graphql.Assert;
import graphql.AssertException;
import graphql.DirectivesUtil;
import graphql.Internal;
import graphql.PublicApi;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.InterfaceTypeExtensionDefinition;
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
import static graphql.Assert.assertValidName;
import static graphql.schema.GraphqlTypeComparators.sortTypes;
import static graphql.util.FpKit.getByName;
import static graphql.util.FpKit.valuesToList;
import static java.lang.String.format;
import static java.util.Collections.emptyList;

/**
 * In graphql, an interface is an abstract type that defines the set of fields that a type must include to
 * implement that interface.
 * <p>
 * At runtime a {@link graphql.schema.TypeResolver} is used to take an interface object value and decide what {@link graphql.schema.GraphQLObjectType}
 * represents this interface type.
 * <p>
 * See http://graphql.org/learn/schema/#interfaces for more details on the concept.
 */
@PublicApi
public class GraphQLInterfaceType implements GraphQLNamedType, GraphQLCompositeType, GraphQLUnmodifiedType, GraphQLNullableType, GraphQLDirectiveContainer, GraphQLImplementingType {

    private final String name;
    private final String description;
    private final Map<String, GraphQLFieldDefinition> fieldDefinitionsByName = new LinkedHashMap<>();
    private final TypeResolver typeResolver;
    private final InterfaceTypeDefinition definition;
    private final ImmutableList<InterfaceTypeExtensionDefinition> extensionDefinitions;
    private final DirectivesUtil.DirectivesHolder directivesHolder;

    private final ImmutableList<GraphQLNamedOutputType> originalInterfaces;
    private final Comparator<? super GraphQLSchemaElement> interfaceComparator;
    private ImmutableList<GraphQLNamedOutputType> replacedInterfaces;


    public static final String CHILD_FIELD_DEFINITIONS = "fieldDefinitions";
    public static final String CHILD_INTERFACES = "interfaces";

    @Internal
    private GraphQLInterfaceType(String name,
                                 String description,
                                 List<GraphQLFieldDefinition> fieldDefinitions,
                                 TypeResolver typeResolver,
                                 List<GraphQLDirective> directives,
                                 List<GraphQLAppliedDirective> appliedDirectives,
                                 InterfaceTypeDefinition definition,
                                 List<InterfaceTypeExtensionDefinition> extensionDefinitions,
                                 List<GraphQLNamedOutputType> interfaces,
                                 Comparator<? super GraphQLSchemaElement> interfaceComparator) {
        assertValidName(name);
        assertNotNull(fieldDefinitions, () -> "fieldDefinitions can't null");
        assertNotNull(directives, () -> "directives cannot be null");

        this.name = name;
        this.description = description;
        this.typeResolver = typeResolver;
        this.definition = definition;
        this.interfaceComparator = interfaceComparator;
        this.originalInterfaces = ImmutableList.copyOf(sortTypes(interfaceComparator, interfaces));
        this.extensionDefinitions = ImmutableList.copyOf(extensionDefinitions);
        this.directivesHolder = new DirectivesUtil.DirectivesHolder(directives, appliedDirectives);
        buildDefinitionMap(fieldDefinitions);
    }

    private void buildDefinitionMap(List<GraphQLFieldDefinition> fieldDefinitions) {
        for (GraphQLFieldDefinition fieldDefinition : fieldDefinitions) {
            String name = fieldDefinition.getName();
            if (fieldDefinitionsByName.containsKey(name)) {
                throw new AssertException(format("Duplicated definition for field '%s' in interface '%s'", name, this.name));
            }
            fieldDefinitionsByName.put(name, fieldDefinition);
        }
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
    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    // to be removed in a future version when all code is in the code registry
    TypeResolver getTypeResolver() {
        return typeResolver;
    }

    public InterfaceTypeDefinition getDefinition() {
        return definition;
    }

    public List<InterfaceTypeExtensionDefinition> getExtensionDefinitions() {
        return extensionDefinitions;
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
    public String toString() {
        return "GraphQLInterfaceType{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", fieldDefinitionsByName=" + fieldDefinitionsByName.keySet() +
                ", typeResolver=" + typeResolver +
                '}';
    }

    /**
     * This helps you transform the current GraphQLInterfaceType into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new object based on calling build on that builder
     */
    public GraphQLInterfaceType transform(Consumer<Builder> builderConsumer) {
        Builder builder = newInterface(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @Override
    public GraphQLSchemaElement copy() {
        return newInterface(this).build();
    }


    @Override
    public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
        return visitor.visitGraphQLInterfaceType(this, context);
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
                .children(CHILD_DIRECTIVES, directivesHolder.getDirectives())
                .children(CHILD_APPLIED_DIRECTIVES, directivesHolder.getAppliedDirectives())
                .build();
    }

    @Override
    public GraphQLInterfaceType withNewChildren(SchemaElementChildrenContainer newChildren) {
        return transform(builder ->
                builder.replaceDirectives(newChildren.getChildren(CHILD_DIRECTIVES))
                        .replaceFields(newChildren.getChildren(CHILD_FIELD_DEFINITIONS))
                        .replaceInterfacesOrReferences(newChildren.getChildren(CHILD_INTERFACES))
                        .replaceDirectives(newChildren.getChildren(CHILD_DIRECTIVES))
                        .replaceAppliedDirectives(newChildren.getChildren(CHILD_APPLIED_DIRECTIVES))
        );
    }

    @Override
    public List<GraphQLNamedOutputType> getInterfaces() {
        if (replacedInterfaces != null) {
            return ImmutableList.copyOf(replacedInterfaces);
        }
        return ImmutableList.copyOf(originalInterfaces);
    }

    void replaceInterfaces(List<GraphQLNamedOutputType> interfaces) {
        this.replacedInterfaces = ImmutableList.copyOf(sortTypes(interfaceComparator, interfaces));
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


    public static Builder newInterface() {
        return new Builder();
    }

    public static Builder newInterface(GraphQLInterfaceType existing) {
        return new Builder(existing);
    }


    @PublicApi
    public static class Builder extends GraphqlDirectivesContainerTypeBuilder<Builder,Builder> {
        private TypeResolver typeResolver;
        private InterfaceTypeDefinition definition;
        private List<InterfaceTypeExtensionDefinition> extensionDefinitions = emptyList();
        private final Map<String, GraphQLFieldDefinition> fields = new LinkedHashMap<>();
        private final Map<String, GraphQLNamedOutputType> interfaces = new LinkedHashMap<>();

        public Builder() {
        }

        public Builder(GraphQLInterfaceType existing) {
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.typeResolver = existing.getTypeResolver();
            this.definition = existing.getDefinition();
            this.extensionDefinitions = existing.getExtensionDefinitions();
            this.fields.putAll(getByName(existing.getFieldDefinitions(), GraphQLFieldDefinition::getName));
            this.interfaces.putAll(getByName(existing.originalInterfaces, GraphQLNamedType::getName));
            copyExistingDirectives(existing);
        }

        public Builder definition(InterfaceTypeDefinition definition) {
            this.definition = definition;
            return this;
        }

        public Builder extensionDefinitions(List<InterfaceTypeExtensionDefinition> extensionDefinitions) {
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
            return field(builder);
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

        public boolean hasField(String fieldName) {
            return fields.containsKey(fieldName);
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


        @Deprecated
        public Builder typeResolver(TypeResolver typeResolver) {
            this.typeResolver = typeResolver;
            return this;
        }

        public Builder replaceInterfaces(List<GraphQLInterfaceType> interfaces) {
            return replaceInterfacesOrReferences(interfaces);
        }

        public Builder replaceInterfacesOrReferences(List<? extends GraphQLNamedOutputType> interfacesOrReferences) {
            assertNotNull(interfacesOrReferences, () -> "interfaces can't be null");
            this.interfaces.clear();
            for (GraphQLNamedOutputType schemaElement : interfacesOrReferences) {
                if (schemaElement instanceof GraphQLInterfaceType || schemaElement instanceof GraphQLTypeReference) {
                    this.interfaces.put(schemaElement.getName(), schemaElement);
                } else {
                    Assert.assertShouldNeverHappen("Unexpected type " + (schemaElement != null ? schemaElement.getClass() : "null"));
                }
            }
            return this;
        }

        public Builder withInterface(GraphQLInterfaceType interfaceType) {
            assertNotNull(interfaceType, () -> "interfaceType can't be null");
            this.interfaces.put(interfaceType.getName(), interfaceType);
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

        public GraphQLInterfaceType build() {
            return new GraphQLInterfaceType(
                    name,
                    description,
                    sort(fields, GraphQLInterfaceType.class, GraphQLFieldDefinition.class),
                    typeResolver,
                    sort(directives, GraphQLInterfaceType.class, GraphQLDirective.class),
                    sort(appliedDirectives, GraphQLScalarType.class, GraphQLAppliedDirective.class),
                    definition,
                    extensionDefinitions,
                    valuesToList(interfaces),
                    getComparator(GraphQLInterfaceType.class, GraphQLInterfaceType.class)
            );
        }
    }
}
