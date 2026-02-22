package graphql.execution.directives;


import graphql.Assert;
import graphql.GraphQLContext;
import graphql.PublicApi;
import graphql.language.Argument;
import graphql.language.Value;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphqlTypeBuilder;
import graphql.schema.InputValueWithState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static graphql.execution.ValuesResolver.getInputValueImpl;

/**
 * This represents the argument values that can be placed on an {@link QueryAppliedDirective}.
 * <p>
 * You can think of them as 'instances' of {@link GraphQLArgument}, when applied to a directive on a query element
 */
@PublicApi
@NullMarked
public class QueryAppliedDirectiveArgument {

    private final String name;
    private final InputValueWithState value;
    private final GraphQLInputType originalType;

    private final @Nullable Argument definition;


    private QueryAppliedDirectiveArgument(String name,
                                          InputValueWithState value,
                                          GraphQLInputType type,
                                          @Nullable Argument definition
    ) {
        assertValidName(name);
        this.name = name;
        this.value = assertNotNull(value);
        this.originalType = assertNotNull(type);
        this.definition = definition;
    }

    public String getName() {
        return name;
    }

    public GraphQLInputType getType() {
        return originalType;
    }

    public boolean hasSetValue() {
        return value.isSet();
    }

    /**
     * @return an input value with state for an applied directive argument
     */
    public InputValueWithState getArgumentValue() {
        return value;
    }

    /**
     * This swill give out an internal java value based on the semantics captured
     * in the {@link InputValueWithState} from {@link QueryAppliedDirectiveArgument#getArgumentValue()}
     *
     * Note : You MUST only call this on a {@link QueryAppliedDirectiveArgument} that is part of a fully formed schema.  We need
     * all the types to be resolved in order for this work correctly.
     *
     * Note: This method will return null if the value is not set or explicitly set to null.  If you want to know the difference
     * when "not set" and "set to null" then you can't use this method.  Rather you should use {@link QueryAppliedDirectiveArgument#getArgumentValue()}
     * and use the {@link InputValueWithState#isNotSet()} methods to decide how to handle those values.
     *
     * @param <T> the type you want it cast as
     *
     * @return a value of type T which is the java value of the argument
     */
    public @Nullable <T> T getValue() {
        return getInputValueImpl(getType(), value, GraphQLContext.getDefault(), Locale.getDefault());
    }

    /**
     * This will always be null.  Applied arguments have no description
     *
     * @return always null
     */
    public @Nullable String getDescription() {
        return null;
    }

    public @Nullable Argument getDefinition() {
        return definition;
    }


    /**
     * This helps you transform the current GraphQLArgument into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new field based on calling build on that builder
     */
    public QueryAppliedDirectiveArgument transform(Consumer<Builder> builderConsumer) {
        Builder builder = newArgument(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static Builder newArgument() {
        return new Builder();
    }

    public static Builder newArgument(QueryAppliedDirectiveArgument existing) {
        return new Builder(existing);
    }

    @Override
    public String toString() {
        return "QueryAppliedDirectiveArgument{" +
                "name='" + name + '\'' +
                ", type=" + getType() +
                ", value=" + value +
                '}';
    }

    @NullUnmarked
    public static class Builder extends GraphqlTypeBuilder<Builder> {

        private InputValueWithState value = InputValueWithState.NOT_SET;
        private Argument definition;
        private GraphQLInputType type;

        public Builder() {
        }

        public Builder(QueryAppliedDirectiveArgument existing) {
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
        public Builder valueLiteral(Value<?> value) {
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

        public Builder inputValueWithState(InputValueWithState value) {
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


        public QueryAppliedDirectiveArgument build() {
            return new QueryAppliedDirectiveArgument(
                    name,
                    value,
                    type,
                    definition
            );
        }
    }
}
