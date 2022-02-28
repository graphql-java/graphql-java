package graphql.schema;


import graphql.Assert;
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

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static graphql.execution.ValuesResolver.getInputValueImpl;

/**
 * This represents the argument values that can be placed on an {@link GraphQLAppliedDirective}.
 * <p>
 * You can think of them as 'instances' of {@link GraphQLArgument}, when applied to a directive on a schema element
 */
@PublicApi
public class GraphQLAppliedDirectiveArgument implements GraphQLNamedSchemaElement {

    private final String name;
    private final InputValueWithState value;
    private final GraphQLInputType originalType;
    private GraphQLInputType replacedType;

    private final Argument definition;


    private GraphQLAppliedDirectiveArgument(String name,
                                            InputValueWithState value,
                                            GraphQLInputType type,
                                            Argument definition
    ) {
        assertValidName(name);
        this.name = name;
        this.value = assertNotNull(value);
        this.originalType = assertNotNull(type);
        this.definition = definition;
    }

    @Override
    public String getName() {
        return name;
    }

    public GraphQLInputType getType() {
        return replacedType != null ? replacedType : originalType;
    }

    void replaceType(GraphQLInputType type) {
        this.replacedType = type;
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
     * This swill give out an internal java value based on the semantics captured
     * in the {@link InputValueWithState} from {@link GraphQLAppliedDirectiveArgument#getArgumentValue()}
     *
     * Note : You MUST only call this on a {@link GraphQLAppliedDirectiveArgument} that is part of a fully formed schema.  We need
     * all the types to be resolved in order for this work correctly.
     *
     * Note: This method will return null if the value is not set or explicitly set to null.  If you want to know the difference
     * when "not set" and "set to null" then you can't use this method.  Rather you should use {@link GraphQLAppliedDirectiveArgument#getArgumentValue()}
     * and use the {@link InputValueWithState#isNotSet()} methods to decide how to handle those values.
     *
     * @param <T> the type you want it cast as
     *
     * @return a value of type T which is the java value of the argument
     */
    public <T> T getValue() {
        return getInputValueImpl(getType(), value);
    }

    /**
     * This will always be null.  Applied arguments have no description
     *
     * @return always null
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
    public GraphQLAppliedDirectiveArgument withNewChildren(SchemaElementChildrenContainer newChildren) {
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
    public GraphQLAppliedDirectiveArgument transform(Consumer<Builder> builderConsumer) {
        Builder builder = newArgument(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static Builder newArgument() {
        return new Builder();
    }

    public static Builder newArgument(GraphQLAppliedDirectiveArgument existing) {
        return new Builder(existing);
    }

    @Override
    public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
        return visitor.visitGraphQLAppliedDirectiveArgument(this, context);
    }

    @Override
    public String toString() {
        return "GraphQLAppliedDirectiveArgument{" +
                "name='" + name + '\'' +
                ", type=" + getType() +
                ", value=" + value +
                '}';
    }

    public static class Builder extends GraphqlTypeBuilder<Builder> {

        private InputValueWithState value = InputValueWithState.NOT_SET;
        private Argument definition;
        private GraphQLInputType type;

        public Builder() {
        }

        public Builder(GraphQLAppliedDirectiveArgument existing) {
            this.name = existing.getName();
            this.value = existing.getArgumentValue();
            this.type = existing.getType();
        }

        public Builder type(GraphQLInputType type) {
            this.type = assertNotNull(type);
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

        public Builder inputValueWithState(@NotNull InputValueWithState value) {
            this.value = Assert.assertNotNull(value);
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


        public GraphQLAppliedDirectiveArgument build() {

            return new GraphQLAppliedDirectiveArgument(
                    name,
                    value,
                    type,
                    definition
            );
        }
    }
}
