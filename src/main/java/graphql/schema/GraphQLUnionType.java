package graphql.schema;


import com.google.common.collect.ImmutableList;
import graphql.Assert;
import graphql.DirectivesUtil;
import graphql.Internal;
import graphql.PublicApi;
import graphql.language.UnionTypeDefinition;
import graphql.language.UnionTypeExtensionDefinition;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotEmpty;
import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static graphql.schema.SchemaElementChildrenContainer.newSchemaElementChildrenContainer;
import static graphql.util.FpKit.getByName;
import static java.util.Collections.emptyList;

/**
 * A union type is a polymorphic type that dynamically represents one of more concrete object types.
 * <p>
 * At runtime a {@link graphql.schema.TypeResolver} is used to take an union object value and decide what {@link graphql.schema.GraphQLObjectType}
 * represents this union of types.
 * <p>
 * Note that members of a union type need to be concrete object types; you can't create a union type out of interfaces or other unions.
 * <p>
 * See http://graphql.org/learn/schema/#union-types for more details on the concept.
 */
@PublicApi
public class GraphQLUnionType implements GraphQLNamedOutputType, GraphQLCompositeType, GraphQLUnmodifiedType, GraphQLNullableType, GraphQLDirectiveContainer {

    private final String name;
    private final String description;
    private final ImmutableList<GraphQLNamedOutputType> originalTypes;
    private final TypeResolver typeResolver;
    private final UnionTypeDefinition definition;
    private final ImmutableList<UnionTypeExtensionDefinition> extensionDefinitions;
    private final DirectivesUtil.DirectivesHolder directives;

    private ImmutableList<GraphQLNamedOutputType> replacedTypes;

    public static final String CHILD_TYPES = "types";
    public static final String CHILD_DIRECTIVES = "directives";

    @Internal
    private GraphQLUnionType(String name, String description, List<GraphQLNamedOutputType> types, TypeResolver typeResolver, List<GraphQLDirective> directives, UnionTypeDefinition definition, List<UnionTypeExtensionDefinition> extensionDefinitions) {
        assertValidName(name);
        assertNotNull(types, () -> "types can't be null");
        assertNotEmpty(types, () -> "A Union type must define one or more member types.");
        assertNotNull(directives, () -> "directives cannot be null");

        this.name = name;
        this.description = description;
        this.originalTypes = ImmutableList.copyOf(types);
        this.typeResolver = typeResolver;
        this.definition = definition;
        this.extensionDefinitions = ImmutableList.copyOf(extensionDefinitions);
        this.directives = new DirectivesUtil.DirectivesHolder(directives);
    }

    void replaceTypes(List<GraphQLNamedOutputType> types) {
        this.replacedTypes = ImmutableList.copyOf(types);
    }

    /**
     * @return This returns GraphQLObjectType or GraphQLTypeReference instances, if the type
     * references are not resolved yet. After they are resolved it contains only GraphQLObjectType.
     * Reference resolving happens when a full schema is built.
     */
    public List<GraphQLNamedOutputType> getTypes() {
        if (replacedTypes != null) {
            return replacedTypes;
        }
        return originalTypes;
    }

    /**
     * Returns true of the object type is a member of this Union type.
     *
     * @param graphQLObjectType the type to check
     *
     * @return true if the object type is a member of this union type.
     */
    public boolean isPossibleType(GraphQLObjectType graphQLObjectType) {
        return getTypes().stream().anyMatch(nt -> nt.getName().equals(graphQLObjectType.getName()));
    }

    // to be removed in a future version when all code is in the code registry
    TypeResolver getTypeResolver() {
        return typeResolver;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public UnionTypeDefinition getDefinition() {
        return definition;
    }

    public List<UnionTypeExtensionDefinition> getExtensionDefinitions() {
        return extensionDefinitions;
    }

    @Override
    public List<GraphQLDirective> getDirectives() {
        return directives.getDirectives();
    }

    @Override
    public Map<String, GraphQLDirective> getDirectivesByName() {
        return directives.getDirectivesByName();
    }

    @Override
    public Map<String, List<GraphQLDirective>> getAllDirectivesByName() {
        return directives.getAllDirectivesByName();
    }

    @Override
    public GraphQLDirective getDirective(String directiveName) {
        return directives.getDirective(directiveName);
    }

    /**
     * This helps you transform the current GraphQLUnionType into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new object based on calling build on that builder
     */
    public GraphQLUnionType transform(Consumer<Builder> builderConsumer) {
        Builder builder = newUnionType(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @Override
    public GraphQLSchemaElement copy() {
        return newUnionType(this).build();
    }


    @Override
    public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
        return visitor.visitGraphQLUnionType(this, context);
    }

    @Override
    public List<GraphQLSchemaElement> getChildren() {
        List<GraphQLSchemaElement> children = new ArrayList<>(getTypes());
        children.addAll(directives.getDirectives());
        return children;
    }

    @Override
    public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
        return newSchemaElementChildrenContainer()
                .children(CHILD_TYPES, originalTypes)
                .children(CHILD_DIRECTIVES, directives.getDirectives())
                .build();
    }

    @Override
    public GraphQLUnionType withNewChildren(SchemaElementChildrenContainer newChildren) {
        return transform(builder ->
                builder.replaceDirectives(newChildren.getChildren(CHILD_DIRECTIVES))
                        .replacePossibleTypes(newChildren.getChildren(CHILD_TYPES))
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


    public static Builder newUnionType() {
        return new Builder();
    }

    public static Builder newUnionType(GraphQLUnionType existing) {
        return new Builder(existing);
    }

    @PublicApi
    public static class Builder extends GraphqlTypeBuilder {
        private TypeResolver typeResolver;
        private UnionTypeDefinition definition;
        private List<UnionTypeExtensionDefinition> extensionDefinitions = emptyList();

        private final Map<String, GraphQLNamedOutputType> types = new LinkedHashMap<>();
        private final List<GraphQLDirective> directives = new ArrayList<>();

        public Builder() {
        }

        public Builder(GraphQLUnionType existing) {
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.typeResolver = existing.getTypeResolver();
            this.definition = existing.getDefinition();
            this.extensionDefinitions = existing.getExtensionDefinitions();
            this.types.putAll(getByName(existing.originalTypes, GraphQLNamedType::getName));
            DirectivesUtil.enforceAddAll(this.directives,existing.getDirectives());
        }

        @Override
        public Builder name(String name) {
            super.name(name);
            return this;
        }

        @Override
        public Builder description(String description) {
            super.description(description);
            return this;
        }

        @Override
        public Builder comparatorRegistry(GraphqlTypeComparatorRegistry comparatorRegistry) {
            super.comparatorRegistry(comparatorRegistry);
            return this;
        }

        public Builder definition(UnionTypeDefinition definition) {
            this.definition = definition;
            return this;
        }

        public Builder extensionDefinitions(List<UnionTypeExtensionDefinition> extensionDefinitions) {
            this.extensionDefinitions = extensionDefinitions;
            return this;
        }

        @Deprecated
        public Builder typeResolver(TypeResolver typeResolver) {
            this.typeResolver = typeResolver;
            return this;
        }


        public Builder possibleType(GraphQLObjectType type) {
            assertNotNull(type, () -> "possible type can't be null");
            types.put(type.getName(), type);
            return this;
        }

        public Builder possibleType(GraphQLTypeReference reference) {
            assertNotNull(reference, () -> "reference can't be null");
            types.put(reference.getName(), reference);
            return this;
        }

        public Builder possibleTypes(GraphQLObjectType... type) {
            for (GraphQLObjectType graphQLType : type) {
                possibleType(graphQLType);
            }
            return this;
        }

        public Builder replacePossibleTypes(List<? extends GraphQLNamedOutputType> types) {
            this.types.clear();
            for (GraphQLSchemaElement schemaElement : types) {
                if (schemaElement instanceof GraphQLTypeReference) {
                    possibleType((GraphQLTypeReference) schemaElement);
                } else if (schemaElement instanceof GraphQLObjectType) {
                    possibleType((GraphQLObjectType) schemaElement);
                } else {
                    Assert.assertShouldNeverHappen("Unexpected type " + (schemaElement != null ? schemaElement.getClass() : "null"));
                }
            }
            return this;
        }

        public Builder possibleTypes(GraphQLTypeReference... references) {
            for (GraphQLTypeReference reference : references) {
                possibleType(reference);
            }
            return this;
        }

        /**
         * This is used to clear all the types in the builder so far.
         *
         * @return the builder
         */
        public Builder clearPossibleTypes() {
            types.clear();
            return this;
        }

        public boolean containType(String name) {
            return types.containsKey(name);
        }

        public Builder withDirectives(GraphQLDirective... directives) {
            assertNotNull(directives, () -> "directives can't be null");
            this.directives.clear();
            for (GraphQLDirective directive : directives) {
                withDirective(directive);
            }
            return this;
        }

        public Builder replaceDirectives(List<GraphQLDirective> directives) {
            assertNotNull(directives, () -> "directive can't be null");
            this.directives.clear();
            DirectivesUtil.enforceAddAll(this.directives, directives);
            return this;
        }

        public Builder withDirective(GraphQLDirective directive) {
            assertNotNull(directive, () -> "directive can't be null");
            DirectivesUtil.enforceAdd(this.directives, directive);
            return this;
        }

        public Builder withDirective(GraphQLDirective.Builder builder) {
            return withDirective(builder.build());
        }

        /**
         * This is used to clear all the directives in the builder so far.
         *
         * @return the builder
         */
        public Builder clearDirectives() {
            directives.clear();
            return this;
        }

        public GraphQLUnionType build() {
            return new GraphQLUnionType(
                    name,
                    description,
                    sort(types, GraphQLUnionType.class, GraphQLOutputType.class),
                    typeResolver,
                    sort(directives, GraphQLUnionType.class, GraphQLDirective.class),
                    definition,
                    extensionDefinitions);
        }
    }

    @Override
    public String toString() {
        return "GraphQLUnionType{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", definition=" + definition +
                '}';
    }
}
