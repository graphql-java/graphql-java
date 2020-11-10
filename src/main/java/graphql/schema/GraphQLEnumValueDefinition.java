package graphql.schema;


import com.google.common.collect.ImmutableList;
import graphql.DirectivesUtil;
import graphql.Internal;
import graphql.PublicApi;
import graphql.language.EnumValueDefinition;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static graphql.util.FpKit.getByName;
import static graphql.util.FpKit.valuesToList;
import static java.util.Collections.emptyList;

/**
 * A graphql enumeration type has a limited set of values and this defines one of those unique values
 *
 * See http://graphql.org/learn/schema/#enumeration-types for more details
 *
 * @see graphql.schema.GraphQLEnumType
 */
@PublicApi
public class GraphQLEnumValueDefinition implements GraphQLNamedSchemaElement, GraphQLDirectiveContainer {

    private final String name;
    private final String description;
    private final Object value;
    private final String deprecationReason;
    private final ImmutableList<GraphQLDirective> directives;
    private final EnumValueDefinition definition;

    public static final String CHILD_DIRECTIVES = "directives";

    /**
     * @param name        the name
     * @param description the description
     * @param value       the value
     *
     * @deprecated use the {@link #newEnumValueDefinition()}   builder pattern instead, as this constructor will be made private in a future version.
     */
    @Internal
    @Deprecated
    public GraphQLEnumValueDefinition(String name, String description, Object value) {
        this(name, description, value, null, emptyList());
    }

    /**
     * @param name              the name
     * @param description       the description
     * @param value             the value
     * @param deprecationReason the deprecation reasons
     *
     * @deprecated use the {@link #newEnumValueDefinition()}   builder pattern instead, as this constructor will be made private in a future version.
     */
    @Internal
    @Deprecated
    public GraphQLEnumValueDefinition(String name, String description, Object value, String deprecationReason) {
        this(name, description, value, deprecationReason, emptyList());
    }

    /**
     * @param name              the name
     * @param description       the description
     * @param value             the value
     * @param deprecationReason the deprecation reasons
     * @param directives        the directives on this type element
     *
     * @deprecated use the {@link #newEnumValueDefinition()}   builder pattern instead, as this constructor will be made private in a future version.
     */
    @Internal
    @Deprecated
    public GraphQLEnumValueDefinition(String name, String description, Object value, String deprecationReason, List<GraphQLDirective> directives) {
        this(name, description, value, deprecationReason, directives, null);
    }

    private GraphQLEnumValueDefinition(String name, String description, Object value, String deprecationReason, List<GraphQLDirective> directives, EnumValueDefinition definition) {
        assertValidName(name);
        assertNotNull(directives, () -> "directives cannot be null");

        this.name = name;
        this.description = description;
        this.value = value;
        this.deprecationReason = deprecationReason;
        this.directives = ImmutableList.copyOf(directives);
        this.definition = definition;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Object getValue() {
        return value;
    }

    public boolean isDeprecated() {
        return deprecationReason != null;
    }

    public String getDeprecationReason() {
        return deprecationReason;
    }

    @Override
    public List<GraphQLDirective> getDirectives() {
        return directives;
    }

    @Override
    public Map<String, GraphQLDirective> getDirectivesByName() {
        return DirectivesUtil.directivesByName(directives);
    }

    @Override
    public GraphQLDirective getDirective(String directiveName) {
        return DirectivesUtil.directiveByName(directives, directiveName).orElse(null);
    }

    public EnumValueDefinition getDefinition() {
        return definition;
    }

    /**
     * This helps you transform the current GraphQLEnumValueDefinition into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new field based on calling build on that builder
     */
    public GraphQLEnumValueDefinition transform(Consumer<Builder> builderConsumer) {
        Builder builder = newEnumValueDefinition(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @Override
    public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
        return visitor.visitGraphQLEnumValueDefinition(this, context);
    }

    @Override
    public List<GraphQLSchemaElement> getChildren() {
        return ImmutableList.copyOf(directives);
    }

    @Override
    public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
        return SchemaElementChildrenContainer.newSchemaElementChildrenContainer()
                .children(CHILD_DIRECTIVES, directives)
                .build();
    }

    @Override
    public GraphQLEnumValueDefinition withNewChildren(SchemaElementChildrenContainer newChildren) {
        return transform(builder ->
                builder.replaceDirectives(newChildren.getChildren(CHILD_DIRECTIVES))
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


    public static Builder newEnumValueDefinition() {
        return new Builder();
    }

    public static Builder newEnumValueDefinition(GraphQLEnumValueDefinition existing) {
        return new Builder(existing);
    }

    @PublicApi
    public static class Builder extends GraphqlTypeBuilder {
        private Object value;
        private String deprecationReason;
        private EnumValueDefinition definition;
        private final Map<String, GraphQLDirective> directives = new LinkedHashMap<>();

        public Builder() {
        }

        public Builder(GraphQLEnumValueDefinition existing) {
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.value = existing.getValue();
            this.deprecationReason = existing.getDeprecationReason();
            this.directives.putAll(getByName(existing.getDirectives(), GraphQLDirective::getName));
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

        public Builder value(Object value) {
            this.value = value;
            return this;
        }

        public Builder deprecationReason(String deprecationReason) {
            this.deprecationReason = deprecationReason;
            return this;
        }

        public Builder definition(EnumValueDefinition definition) {
            this.definition = definition;
            return this;
        }

        public Builder withDirectives(GraphQLDirective... directives) {
            assertNotNull(directives, () -> "directives can't be null");
            for (GraphQLDirective directive : directives) {
                withDirective(directive);
            }
            return this;
        }

        public Builder withDirective(GraphQLDirective directive) {
            assertNotNull(directive, () -> "directive can't be null");
            directives.put(directive.getName(), directive);
            return this;
        }

        public Builder replaceDirectives(List<GraphQLDirective> directives) {
            assertNotNull(directives, () -> "directive can't be null");
            this.directives.clear();
            for (GraphQLDirective directive : directives) {
                this.directives.put(directive.getName(), directive);
            }
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

        public GraphQLEnumValueDefinition build() {
            return new GraphQLEnumValueDefinition(name, description, value, deprecationReason, valuesToList(directives), definition);
        }
    }
}
