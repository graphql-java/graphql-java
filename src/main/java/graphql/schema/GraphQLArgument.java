package graphql.schema;


import graphql.DirectivesUtil;
import graphql.PublicApi;
import graphql.language.InputValueDefinition;
import graphql.language.Value;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static graphql.execution.ValuesResolver.getInputValueImpl;

/**
 * This defines an argument that can be supplied to a graphql field (via {@link graphql.schema.GraphQLFieldDefinition}.
 * <p>
 * Fields can be thought of as "functions" that take arguments and return a value.
 * <p>
 * See http://graphql.org/learn/queries/#arguments for more details on the concept.
 * <p>
 * {@link graphql.schema.GraphQLArgument} is used in two contexts, one context is graphql queries where it represents the arguments that can be
 * set on a field and the other is in Schema Definition Language (SDL) where it can be used to represent the argument value instances
 * that have been supplied on a {@link graphql.schema.GraphQLDirective}.
 * <p>
 * The difference is the 'value' and 'defaultValue' properties.  In a query argument, the 'value' is never in the GraphQLArgument
 * object but rather in the AST direct or in the query variables map and the 'defaultValue' represents a value to use if both of these are
 * not present. You can think of them like a descriptor of what shape an argument might have.
 * <p>
 * However with directives on SDL elements, the value is specified in AST only and transferred into the GraphQLArgument object and the
 * 'defaultValue' comes instead from the directive definition elsewhere in the SDL.  You can think of them as 'instances' of arguments, their shape and their
 * specific value on that directive.
 */
@PublicApi
public class GraphQLArgument implements GraphQLNamedSchemaElement, GraphQLInputValueDefinition {

    private final String name;
    private final String description;
    private final String deprecationReason;
    private final GraphQLInputType originalType;

    private final InputValueWithState defaultValue;
    private final InputValueWithState value;

    private final InputValueDefinition definition;
    private final DirectivesUtil.DirectivesHolder directives;

    private GraphQLInputType replacedType;

    public static final String CHILD_DIRECTIVES = "directives";
    public static final String CHILD_TYPE = "type";


    private GraphQLArgument(String name,
                            String description,
                            GraphQLInputType type,
                            InputValueWithState defaultValue,
                            InputValueWithState value,
                            InputValueDefinition definition,
                            List<GraphQLDirective> directives,
                            String deprecationReason) {
        assertValidName(name);
        assertNotNull(type, () -> "type can't be null");
        this.name = name;
        this.description = description;
        this.originalType = type;
        this.defaultValue = defaultValue;
        this.value = value;
        this.definition = definition;
        this.deprecationReason = deprecationReason;
        this.directives = new DirectivesUtil.DirectivesHolder(directives);
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

    /**
     * The default value of this argument.
     *
     * @return a {@link InputValueWithState} that represents the arguments default value
     */
    public @NotNull InputValueWithState getArgumentDefaultValue() {
        return defaultValue;
    }

    public boolean hasSetDefaultValue() {
        return defaultValue.isSet();
    }

    public boolean hasSetValue() {
        return value.isSet();
    }


    /**
     * This is only used for applied directives.
     *
     * @return an input value with state for an applied directive
     */
    public @NotNull InputValueWithState getArgumentValue() {
        return value;
    }

    /**
     * This static helper method will give out a java value based on the semantics captured
     * in the {@link InputValueWithState} from {@link GraphQLArgument#getArgumentValue()}
     *
     * Note : You MUST only call this on a {@link GraphQLArgument} that is part of a fully formed schema.  We need
     * all of the types to be resolved in order for this work correctly.
     *
     * Note: This method will return null if the value is not set or explicitly set to null.  If you you to know the difference
     * when "not set" and "set to null" then you cant use this method.  Rather you should use {@link GraphQLArgument#getArgumentValue()}
     * and use the {@link InputValueWithState#isNotSet()} methods to decide how to handle those values.
     *
     * @param argument the fully formed {@link GraphQLArgument}
     * @param <T>      the type you want it cast as
     *
     * @return a value of type T which is the java value of the argument
     */
    public static <T> T getArgumentValue(GraphQLArgument argument) {
        return getInputValueImpl(argument.getType(), argument.getArgumentValue());
    }

    /**
     * This static helper method will give out a java value based on the semantics captured
     * in the {@link InputValueWithState} from {@link GraphQLArgument#getArgumentDefaultValue()}
     *
     * Note : You MUST only call this on a {@link GraphQLArgument} that is part of a fully formed schema.  We need
     * all of the types to be resolved in order for this work correctly.
     *
     * Note: This method will return null if the value is not set or explicitly set to null.  If you you to know the difference
     * when "not set" and "set to null" then you cant use this method.  Rather you should use {@link GraphQLArgument#getArgumentDefaultValue()}
     * and use the {@link InputValueWithState#isNotSet()} methods to decide how to handle those values.
     *
     * @param argument the fully formed {@link GraphQLArgument}
     * @param <T>      the type you want it cast as
     *
     * @return a value of type T which is the java value of the argument default
     */
    public static <T> T getArgumentDefaultValue(GraphQLArgument argument) {
        return getInputValueImpl(argument.getType(), argument.getArgumentDefaultValue());
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
    public GraphQLArgument withNewChildren(SchemaElementChildrenContainer newChildren) {
        return transform(builder ->
                builder.type(newChildren.getChildOrNull(CHILD_TYPE))
                        .replaceDirectives(newChildren.getChildren(CHILD_DIRECTIVES)));
    }

    @Override
    public GraphQLSchemaElement copy() {
        return newArgument(this).build();
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


    /**
     * This helps you transform the current GraphQLArgument into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new field based on calling build on that builder
     */
    public GraphQLArgument transform(Consumer<Builder> builderConsumer) {
        Builder builder = newArgument(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static Builder newArgument() {
        return new Builder();
    }

    public static Builder newArgument(GraphQLArgument existing) {
        return new Builder(existing);
    }

    @Override
    public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
        return visitor.visitGraphQLArgument(this, context);
    }

    @Override
    public String toString() {
        return "GraphQLArgument{" +
                "name='" + name + '\'' +
                ", value=" + value +
                ", defaultValue=" + defaultValue +
                ", type=" + getType() +
                '}';
    }

    public static class Builder extends GraphqlTypeBuilder {

        private GraphQLInputType type;
        private InputValueWithState defaultValue = InputValueWithState.NOT_SET;
        private InputValueWithState value = InputValueWithState.NOT_SET;
        private String deprecationReason;
        private InputValueDefinition definition;
        private final List<GraphQLDirective> directives = new ArrayList<>();


        public Builder() {
        }

        public Builder(GraphQLArgument existing) {
            this.name = existing.getName();
            this.type = existing.originalType;
            this.value = existing.getArgumentValue();
            this.defaultValue = existing.defaultValue;
            this.description = existing.getDescription();
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

        public Builder type(GraphQLInputType type) {
            this.type = type;
            return this;
        }

        /**
         * A legacy method that sets a default value into the argument
         *
         * @param defaultValue a default value
         *
         * @return this builder
         *
         * @deprecated use {@link #defaultValueLiteral(Value)} or {@link #defaultValueProgrammatic(Object)}
         */
        @Deprecated
        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = InputValueWithState.newInternalValue(defaultValue);
            return this;
        }

        /**
         * @param defaultValue can't be null as a `null` is represented a @{@link graphql.language.NullValue} Literal
         *
         * @return this builder
         */
        public Builder defaultValueLiteral(@NotNull Value defaultValue) {
            this.defaultValue = InputValueWithState.newLiteralValue(defaultValue);
            return this;
        }

        /**
         * @param defaultValue Can be null to represent null value
         *
         * @return this builder
         */
        public Builder defaultValueProgrammatic(@Nullable Object defaultValue) {
            this.defaultValue = InputValueWithState.newExternalValue(defaultValue);
            return this;
        }

        /**
         * Removes the defaultValue to represent a missing default value (which is different from null)
         *
         * @return this builder
         */
        public Builder clearDefaultValue() {
            this.defaultValue = InputValueWithState.NOT_SET;
            return this;
        }

        /**
         * A legacy method for setting an arguments value
         *
         * @param value the argument value
         *
         * @return this builder
         *
         * @deprecated use {@link #valueLiteral(Value)} or {@link #valueProgrammatic(Object)}
         */
        @Deprecated
        public Builder value(@Nullable Object value) {
            this.value = InputValueWithState.newInternalValue(value);
            return this;
        }

        /**
         * Sets a literal AST value as the arguments value
         *
         * @param value can't be null as a `null` is represented a @{@link graphql.language.NullValue} Literal
         *
         * @return this builder
         */
        public Builder valueLiteral(@NotNull Value value) {
            this.value = InputValueWithState.newLiteralValue(value);
            return this;
        }

        /**
         * @param value values can be null to represent null value
         *
         * @return this builder
         */
        public Builder valueProgrammatic(@Nullable Object value) {
            this.value = InputValueWithState.newExternalValue(value);
            return this;
        }

        /**
         * Removes the value to represent a missing value (which is different from null)
         *
         * @return this builder
         */
        public Builder clearValue() {
            this.value = InputValueWithState.NOT_SET;
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


        public GraphQLArgument build() {
            assertNotNull(type, () -> "type can't be null");

            return new GraphQLArgument(
                    name,
                    description,
                    type,
                    defaultValue,
                    value,
                    definition,
                    sort(directives, GraphQLArgument.class, GraphQLDirective.class),
                    deprecationReason
            );
        }
    }
}
