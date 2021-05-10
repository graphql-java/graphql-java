package graphql.schema;


import graphql.DirectivesUtil;
import graphql.Internal;
import graphql.PublicApi;
import graphql.language.InputValueDefinition;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static java.util.Collections.emptyList;

/**
 * Input objects defined via {@link graphql.schema.GraphQLInputObjectType} contains these input fields.
 *
 * There are similar to {@link graphql.schema.GraphQLFieldDefinition} however they can ONLY be used on input objects, that
 * is to describe values that are fed into a graphql mutation.
 *
 * See http://graphql.org/learn/schema/#input-types for more details on the concept.
 */
@PublicApi
public class GraphQLInputObjectField implements GraphQLNamedSchemaElement, GraphQLInputValueDefinition {

    private final String name;
    private final String description;
    private final GraphQLInputType originalType;
    private final Object defaultValue;
    private final String deprecationReason;
    private final InputValueDefinition definition;
    private final DirectivesUtil.DirectivesHolder directives;

    private GraphQLInputType replacedType;

    public static final String CHILD_TYPE = "type";
    public static final String CHILD_DIRECTIVES = "directives";

    private static final Object DEFAULT_VALUE_SENTINEL = new Object() {
    };


    /**
     * @param name the name
     * @param type the field type
     *
     * @deprecated use the {@link #newInputObjectField()} builder pattern instead, as this constructor will be made private in a future version.
     */
    @Internal
    @Deprecated
    public GraphQLInputObjectField(String name, GraphQLInputType type) {
        this(name, null, type, null, emptyList(), null);
    }

    /**
     * @param name         the name
     * @param description  the description
     * @param type         the field type
     * @param defaultValue the default value
     *
     * @deprecated use the {@link #newInputObjectField()} builder pattern instead, as this constructor will be made private in a future version.
     */
    @Internal
    @Deprecated
    public GraphQLInputObjectField(String name, String description, GraphQLInputType type, Object defaultValue) {
        this(name, description, type, defaultValue, emptyList(), null);
    }

    /**
     * @param name         the name
     * @param description  the description
     * @param type         the field type
     * @param defaultValue the default value
     * @param directives   the directives on this type element
     * @param definition   the AST definition
     *
     * @deprecated use the {@link #newInputObjectField()} builder pattern instead, as this constructor will be made private in a future version.
     */
    @Internal
    @Deprecated
    public GraphQLInputObjectField(String name, String description, GraphQLInputType type, Object defaultValue, List<GraphQLDirective> directives, InputValueDefinition definition) {
        this(name, description, type, defaultValue, directives, definition, null);
    }

    private GraphQLInputObjectField(String name, String description, GraphQLInputType type, Object defaultValue, List<GraphQLDirective> directives, InputValueDefinition definition, String deprecationReason) {
        assertValidName(name);
        assertNotNull(type, () -> "type can't be null");
        assertNotNull(directives, () -> "directives cannot be null");

        this.name = name;
        this.originalType = type;
        this.defaultValue = defaultValue;
        this.description = description;
        this.directives = new DirectivesUtil.DirectivesHolder(directives);
        this.definition = definition;
        this.deprecationReason = deprecationReason;
    }

    void replaceType(GraphQLInputType type) {
        this.replacedType = type;
    }

    @Override
    public String getName() {
        return name;
    }

    public GraphQLInputType getType() {
        return replacedType != null ? replacedType : originalType;
    }

    public Object getDefaultValue() {
        return defaultValue == DEFAULT_VALUE_SENTINEL ? null : defaultValue;
    }

    public boolean hasSetDefaultValue() {
        return defaultValue != DEFAULT_VALUE_SENTINEL;
    }

    public String getDescription() {
        return description;
    }

    public String getDeprecationReason() {
        return deprecationReason;
    }

    public boolean isDeprecated() {
        return deprecationReason != null;
    }

    public InputValueDefinition getDefinition() {
        return definition;
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
     * This helps you transform the current GraphQLInputObjectField into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new object based on calling build on that builder
     */
    public GraphQLInputObjectField transform(Consumer<Builder> builderConsumer) {
        Builder builder = newInputObjectField(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @Override
    public GraphQLSchemaElement copy() {
        return newInputObjectField(this).build();
    }


    @Override
    public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
        return visitor.visitGraphQLInputObjectField(this, context);
    }

    @Override
    public List<GraphQLSchemaElement> getChildren() {
        List<GraphQLSchemaElement> children = new ArrayList<>();
        children.add(getType());
        children.addAll(directives.getDirectives());
        return children;
    }

    @Override
    public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
        return SchemaElementChildrenContainer.newSchemaElementChildrenContainer()
                .children(CHILD_DIRECTIVES, directives.getDirectives())
                .child(CHILD_TYPE, originalType)
                .build();
    }

    @Override
    public GraphQLInputObjectField withNewChildren(SchemaElementChildrenContainer newChildren) {
        return transform(builder ->
                builder.replaceDirectives(newChildren.getChildren(CHILD_DIRECTIVES))
                        .type((GraphQLInputType) newChildren.getChildOrNull(CHILD_TYPE))
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
        return "GraphQLInputObjectField{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", originalType=" + inputTypeToStringAvoidingCircularReference(originalType) +
                ", defaultValue=" + defaultValue +
                ", definition=" + definition +
                ", directives=" + directives +
                ", replacedType=" + inputTypeToStringAvoidingCircularReference(replacedType) +
                '}';
    }

    private static Object inputTypeToStringAvoidingCircularReference(GraphQLInputType graphQLInputType) {
        return (graphQLInputType instanceof GraphQLInputObjectType)
                ? String.format("[%s]", GraphQLInputObjectType.class.getSimpleName())
                : graphQLInputType;
    }

    public static Builder newInputObjectField(GraphQLInputObjectField existing) {
        return new Builder(existing);
    }


    public static Builder newInputObjectField() {
        return new Builder();
    }

    @PublicApi
    public static class Builder extends GraphqlTypeBuilder {
        private Object defaultValue = DEFAULT_VALUE_SENTINEL;
        private GraphQLInputType type;
        private InputValueDefinition definition;
        private final List<GraphQLDirective> directives = new ArrayList<>();
        private String deprecationReason;

        public Builder() {
        }

        public Builder(GraphQLInputObjectField existing) {
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.defaultValue = existing.getDefaultValue();
            this.type = existing.originalType;
            this.definition = existing.getDefinition();
            this.deprecationReason = existing.deprecationReason;
            DirectivesUtil.enforceAddAll(this.directives, existing.getDirectives());
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

        public Builder definition(InputValueDefinition definition) {
            this.definition = definition;
            return this;
        }

        public Builder deprecate(String deprecationReason) {
            this.deprecationReason = deprecationReason;
            return this;
        }

        public Builder type(GraphQLInputObjectType.Builder type) {
            return type(type.build());
        }

        public Builder type(GraphQLInputType type) {
            this.type = type;
            return this;
        }

        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder withDirectives(GraphQLDirective... directives) {
            assertNotNull(directives, () -> "directives can't be null");
            this.directives.clear();
            for (GraphQLDirective directive : directives) {
                withDirective(directive);
            }
            return this;
        }

        public Builder withDirective(GraphQLDirective directive) {
            assertNotNull(directive, () -> "directive can't be null");
            DirectivesUtil.enforceAdd(this.directives, directive);
            return this;
        }

        public Builder replaceDirectives(List<GraphQLDirective> directives) {
            assertNotNull(directives, () -> "directive can't be null");
            this.directives.clear();
            DirectivesUtil.enforceAddAll(this.directives, directives);
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

        public GraphQLInputObjectField build() {
            return new GraphQLInputObjectField(
                    name,
                    description,
                    type,
                    defaultValue,
                    sort(directives, GraphQLInputObjectField.class, GraphQLDirective.class),
                    definition,
                    deprecationReason);
        }
    }
}
