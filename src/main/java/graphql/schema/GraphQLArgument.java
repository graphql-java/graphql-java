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
 * However, with directives on SDL elements, the value is specified in AST only and transferred into the GraphQLArgument object and the
 * 'defaultValue' comes instead from the directive definition elsewhere in the SDL.  You can think of them as 'instances' of arguments, their shape and their
 * specific value on that directive.
 * <p>
 * Originally graphql-java re-used the {@link GraphQLDirective} and {@link GraphQLArgument}
 * classes to do both purposes.  This was a modelling mistake.  New {@link GraphQLAppliedDirective} and {@link GraphQLAppliedDirectiveArgument}
 * classes have been introduced to better model when a directive is applied to a schema element,
 * as opposed to its schema definition itself.
 */
@PublicApi
public class GraphQLArgument implements GraphQLNamedSchemaElement, GraphQLInputValueDefinition {

    private final String name;
    private final String description;
    private final String deprecationReason;
    private final GraphQLInputType originalType;
    private GraphQLInputType replacedType;

    private final InputValueWithState defaultValue;
    private final InputValueWithState value;

    private final InputValueDefinition definition;
    private final DirectivesUtil.DirectivesHolder directivesHolder;


    public static final String CHILD_TYPE = "type";


    private GraphQLArgument(String name,
                            String description,
                            GraphQLInputType type,
                            InputValueWithState defaultValue,
                            InputValueWithState value,
                            InputValueDefinition definition,
                            List<GraphQLDirective> directives,
                            List<GraphQLAppliedDirective> appliedDirectives,
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
        this.directivesHolder = new DirectivesUtil.DirectivesHolder(directives, appliedDirectives);
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
     * This is only used for applied directives, that is when this argument is on a {@link GraphQLDirective} applied to a schema or query element
     *
     * @return an input value with state for an applied directive
     *
     * @deprecated use {@link GraphQLAppliedDirectiveArgument} instead
     */
    @Deprecated
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
     *
     * @deprecated use {@link GraphQLAppliedDirectiveArgument} instead
     */
    @Deprecated
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
    public List<GraphQLSchemaElement> getChildren() {
        List<GraphQLSchemaElement> children = new ArrayList<>();
        children.add(getType());
        children.addAll(directivesHolder.getDirectives());
        children.addAll(directivesHolder.getAppliedDirectives());
        return children;
    }


    @Override
    public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
        return SchemaElementChildrenContainer.newSchemaElementChildrenContainer()
                .child(CHILD_TYPE, originalType)
                .children(CHILD_DIRECTIVES, directivesHolder.getDirectives())
                .children(CHILD_APPLIED_DIRECTIVES, directivesHolder.getAppliedDirectives())
                .build();
    }

    @Override
    public GraphQLArgument withNewChildren(SchemaElementChildrenContainer newChildren) {
        return transform(builder ->
                builder.type(newChildren.getChildOrNull(CHILD_TYPE))
                        .replaceDirectives(newChildren.getChildren(CHILD_DIRECTIVES))
                        .replaceAppliedDirectives(newChildren.getChildren(CHILD_APPLIED_DIRECTIVES))
        );
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

    /**
     * This method can be used to turn an argument that was being use as an applied argument into one.
     *
     * @return an {@link GraphQLAppliedDirectiveArgument}
     */
    public GraphQLAppliedDirectiveArgument toAppliedArgument() {
        return GraphQLAppliedDirectiveArgument.newArgument()
                .name(name)
                .type(getType())
                .inputValueWithState(value)
                .build();
    }

    public static class Builder extends GraphqlDirectivesContainerTypeBuilder<Builder, Builder> {

        private GraphQLInputType type;
        private InputValueWithState defaultValue = InputValueWithState.NOT_SET;
        private InputValueWithState value = InputValueWithState.NOT_SET;
        private String deprecationReason;
        private InputValueDefinition definition;


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
            copyExistingDirectives(existing);
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
         *
         * @deprecated use {@link  GraphQLAppliedDirectiveArgument} methods instead
         */
        @Deprecated
        public Builder valueLiteral(@NotNull Value value) {
            this.value = InputValueWithState.newLiteralValue(value);
            return this;
        }

        /**
         * @param value values can be null to represent null value
         *
         * @return this builder
         *
         * @deprecated use {@link  GraphQLAppliedDirectiveArgument} methods instead
         */
        @Deprecated
        public Builder valueProgrammatic(@Nullable Object value) {
            this.value = InputValueWithState.newExternalValue(value);
            return this;
        }

        /**
         * Removes the value to represent a missing value (which is different from null)
         *
         * @return this builder
         *
         * @deprecated use {@link  GraphQLAppliedDirectiveArgument} methods instead
         */
        @Deprecated
        public Builder clearValue() {
            this.value = InputValueWithState.NOT_SET;
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
                    sort(appliedDirectives, GraphQLScalarType.class, GraphQLAppliedDirective.class),
                    deprecationReason
            );
        }
    }
}
