package graphql.schema;


import graphql.PublicApi;
import graphql.language.Argument;
import graphql.language.Value;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static graphql.Assert.assertValidName;
import static graphql.execution.ValuesResolver.getInputValueImpl;

/**
 * This defines an argument and its value that can be supplied to a graphql field (via {@link GraphQLFieldDefinition} during
 * a query OR it represents the argument values that can be placed on an {@link GraphQLAppliedDirective}.
 * <p>
 * You can think of them as 'instances' of {@link GraphQLArgument}, when applied to some other element
 */
@PublicApi
public class GraphQLAppliedArgument implements GraphQLNamedSchemaElement {

    private final String name;
    private final InputValueWithState value;

    private final Argument definition;


    private GraphQLAppliedArgument(String name,
                                   InputValueWithState value,
                                   Argument definition
    ) {
        assertValidName(name);
        this.name = name;
        this.value = value;
        this.definition = definition;
    }


    @Override
    public String getName() {
        return name;
    }

    public boolean hasSetValue() {
        return value.isSet();
    }

    /**
     * @return an input value with state for an applied directive argument
     */
    public @NotNull InputValueWithState getArgumentValue() {
        return value;
    }

    /**
     * This static helper method will give out a java value based on the semantics captured
     * in the {@link InputValueWithState} from {@link GraphQLAppliedArgument#getArgumentValue()}
     *
     * Note : You MUST only call this on a {@link GraphQLAppliedArgument} that is part of a fully formed schema.  We need
     * all the types to be resolved in order for this work correctly.
     *
     * Note: This method will return null if the value is not set or explicitly set to null.  If you want to know the difference
     * when "not set" and "set to null" then you can't use this method.  Rather you should use {@link GraphQLAppliedArgument#getArgumentValue()}
     * and use the {@link InputValueWithState#isNotSet()} methods to decide how to handle those values.
     *
     * @param argument the fully formed {@link GraphQLAppliedArgument}
     * @param <T>      the type you want it cast as
     *
     * @return a value of type T which is the java value of the argument
     */
    public static <T> T getArgumentValue(GraphQLInputType argumentType, GraphQLAppliedArgument argument) {
        return getInputValueImpl(argumentType, argument.getArgumentValue());
    }

    /**
     * This will always be null
     *
     * @return null
     */
    public @Nullable String getDescription() {
        return null;
    }

    public Argument getDefinition() {
        return definition;
    }

    @Override
    public List<GraphQLSchemaElement> getChildren() {
        return Collections.emptyList();
    }


    @Override
    public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
        return SchemaElementChildrenContainer.newSchemaElementChildrenContainer()
                .build();
    }

    @Override
    public GraphQLAppliedArgument withNewChildren(SchemaElementChildrenContainer newChildren) {
        return this;
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
    public GraphQLAppliedArgument transform(Consumer<Builder> builderConsumer) {
        Builder builder = newArgument(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static Builder newArgument() {
        return new Builder();
    }

    public static Builder newArgument(GraphQLAppliedArgument existing) {
        return new Builder(existing);
    }

    @Override
    public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
        return visitor.visitGraphQLAppliedArgument(this, context);
    }

    @Override
    public String toString() {
        return "GraphQLAppliedDirectiveArgument{" +
                "name='" + name + '\'' +
                ", value=" + value +
                '}';
    }

    public static class Builder extends GraphqlTypeBuilder<Builder> {

        private InputValueWithState value = InputValueWithState.NOT_SET;
        private Argument definition;


        public Builder() {
        }

        public Builder(GraphQLAppliedArgument existing) {
            this.name = existing.getName();
            this.value = existing.getArgumentValue();
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

        public Builder definition(Argument definition) {
            this.definition = definition;
            return this;
        }

        /**
         * Sets a literal AST value as the arguments value
         *
         * @param value can't be null as a `null` is represented a @{@link graphql.language.NullValue} Literal
         *
         * @return this builder
         */
        public Builder valueLiteral(@NotNull Value<?> value) {
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


        public GraphQLAppliedArgument build() {

            return new GraphQLAppliedArgument(
                    name,
                    value,
                    definition
            );
        }
    }
}
