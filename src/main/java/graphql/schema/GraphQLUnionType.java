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

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import static graphql.Assert.assertNotEmpty;
import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static graphql.collect.ImmutableKit.emptyList;
import static graphql.schema.SchemaElementChildrenContainer.newSchemaElementChildrenContainer;
import static graphql.util.FpKit.getByName;

/**
 * A union type is a polymorphic type that dynamically represents one of more concrete object types.
 * <p>
 * At runtime a {@link TypeResolver} is used to take an union object value and decide what {@link GraphQLObjectType}
 * represents this union of types.
 * <p>
 * Note that members of a union type need to be concrete object types; you can't create a union type out of interfaces or other unions.
 * <p>
 * See <a href="https://graphql.org/learn/schema/#union-types">https://graphql.org/learn/schema/#union-types</a> for more details on the concept.
 */
@PublicApi
@NullMarked
public class GraphQLUnionType implements GraphQLNamedOutputType, GraphQLCompositeType, GraphQLUnmodifiedType, GraphQLNullableType, GraphQLDirectiveContainer {

    private final String name;
    private final String description;
    private final ImmutableList<GraphQLNamedOutputType> originalTypes;
    private final TypeResolver typeResolver;
    private final UnionTypeDefinition definition;
    private final ImmutableList<UnionTypeExtensionDefinition> extensionDefinitions;
    private final DirectivesUtil.DirectivesHolder directives;

    private @Nullable ImmutableList<GraphQLNamedOutputType> replacedTypes;

    public static final String CHILD_TYPES = "types";

    @Internal
    private GraphQLUnionType(String name,
                             String description,
                             List<GraphQLNamedOutputType> types,
                             TypeResolver typeResolver,
                             List<GraphQLDirective> directives,
                             List<GraphQLAppliedDirective> appliedDirectives,
                             UnionTypeDefinition definition,
                             List<UnionTypeExtensionDefinition> extensionDefinitions) {
        assertValidName(name);
        assertNotNull(types, "types can't be null");
        assertNotEmpty(types, "A Union type must define one or more member types.");
        assertNotNull(directives, "directives cannot be null");

        this.name = name;
        this.description = description;
        this.originalTypes = ImmutableList.copyOf(types);
        this.typeResolver = typeResolver;
        this.definition = definition;
        this.extensionDefinitions = ImmutableList.copyOf(extensionDefinitions);
        this.directives = DirectivesUtil.DirectivesHolder.create(directives, appliedDirectives);
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
        for (GraphQLNamedOutputType type : getTypes()) {
            if (type.getName().equals(graphQLObjectType.getName())) {
                return true;
            }
        }
        return false;
    }

    // to be removed in a future version when all code is in the code registry
    @Internal
    @Deprecated(since = "2018-12-03")
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

    @Override
    public List<GraphQLAppliedDirective> getAppliedDirectives() {
        return directives.getAppliedDirectives();
    }

    @Override
    public Map<String, List<GraphQLAppliedDirective>> getAllAppliedDirectivesByName() {
        return directives.getAllAppliedDirectivesByName();
    }

    @Override
    public GraphQLAppliedDirective getAppliedDirective(String directiveName) {
        return directives.getAppliedDirective(directiveName);
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
        children.addAll(directives.getAppliedDirectives());
        return children;
    }

    @Override
    public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
        return newSchemaElementChildrenContainer()
                .children(CHILD_TYPES, originalTypes)
                .children(CHILD_DIRECTIVES, directives.getDirectives())
                .children(CHILD_APPLIED_DIRECTIVES, directives.getAppliedDirectives())
                .build();
    }

    @Override
    public GraphQLUnionType withNewChildren(SchemaElementChildrenContainer newChildren) {
        return transform(builder ->
                builder.replacePossibleTypes(newChildren.getChildren(CHILD_TYPES))
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

    @Override
    public String toString() {
        return "GraphQLUnionType{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", definition=" + definition +
                '}';
    }

    public static Builder newUnionType() {
        return new Builder();
    }

    public static Builder newUnionType(GraphQLUnionType existing) {
        return new Builder(existing);
    }

    @PublicApi
    @NullUnmarked
    public static class Builder extends GraphqlDirectivesContainerTypeBuilder<Builder, Builder> {
        private TypeResolver typeResolver;
        private UnionTypeDefinition definition;
        private List<UnionTypeExtensionDefinition> extensionDefinitions = emptyList();
        private final Map<String, GraphQLNamedOutputType> types = new LinkedHashMap<>();

        public Builder() {
        }

        public Builder(GraphQLUnionType existing) {
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.typeResolver = existing.getTypeResolver();
            this.definition = existing.getDefinition();
            this.extensionDefinitions = existing.getExtensionDefinitions();
            this.types.putAll(getByName(existing.originalTypes, GraphQLNamedType::getName));
            copyExistingDirectives(existing);
        }

        public Builder definition(UnionTypeDefinition definition) {
            this.definition = definition;
            return this;
        }

        public Builder extensionDefinitions(List<UnionTypeExtensionDefinition> extensionDefinitions) {
            this.extensionDefinitions = extensionDefinitions;
            return this;
        }

        /**
         * @param typeResolver the type resolver
         *
         * @return this builder
         *
         * @deprecated use {@link graphql.schema.GraphQLCodeRegistry.Builder#typeResolver(GraphQLUnionType, TypeResolver)} instead
         */
        @Deprecated(since = "2018-12-03")
        public Builder typeResolver(TypeResolver typeResolver) {
            this.typeResolver = typeResolver;
            return this;
        }

        public Builder possibleType(GraphQLObjectType type) {
            assertNotNull(type, "possible type can't be null");
            types.put(type.getName(), type);
            return this;
        }

        public Builder possibleType(GraphQLTypeReference reference) {
            assertNotNull(reference, "reference can't be null");
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

        public GraphQLUnionType build() {
            return new GraphQLUnionType(
                    name,
                    description,
                    sort(types, GraphQLUnionType.class, GraphQLOutputType.class),
                    typeResolver,
                    sort(directives, GraphQLUnionType.class, GraphQLDirective.class),
                    sort(appliedDirectives, GraphQLUnionType.class, GraphQLAppliedDirective.class),
                    definition,
                    extensionDefinitions);
        }
    }


}
