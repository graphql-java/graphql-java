package graphql.execution.directives;


import com.google.common.collect.ImmutableList;
import graphql.PublicApi;
import graphql.language.Directive;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphqlTypeBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static graphql.util.FpKit.getByName;

/**
 * An applied directive represents the instance of a directive that is applied to a query element such as a field or fragment.
 * <p>
 * Originally graphql-java re-used the {@link GraphQLDirective} and {@link GraphQLArgument}
 * classes to do both purposes.  This was a modelling mistake.  New {@link QueryAppliedDirective} and {@link QueryAppliedDirectiveArgument}
 * classes have been introduced to better model when a directive is applied to a query element,
 * as opposed to its schema definition itself.
 * <p>
 * See <a href="https://graphql.org/learn/queries/#directives">https://graphql.org/learn/queries/#directives</a> for more details on the concept.
 */
@PublicApi
@NullMarked
public class QueryAppliedDirective {

    private final String name;
    private final ImmutableList<QueryAppliedDirectiveArgument> arguments;
    private final @Nullable Directive definition;

    private QueryAppliedDirective(String name, @Nullable Directive definition, Collection<QueryAppliedDirectiveArgument> arguments) {
        assertValidName(name);
        assertNotNull(arguments, "arguments can't be null");
        this.name = name;
        this.arguments = ImmutableList.copyOf(arguments);
        this.definition = definition;
    }

    public String getName() {
        return name;
    }

    public @Nullable String getDescription() {
        return null;
    }

    public List<QueryAppliedDirectiveArgument> getArguments() {
        return arguments;
    }

    public @Nullable QueryAppliedDirectiveArgument getArgument(String name) {
        for (QueryAppliedDirectiveArgument argument : arguments) {
            if (argument.getName().equals(name)) {
                return argument;
            }
        }
        return null;
    }

    public @Nullable Directive getDefinition() {
        return definition;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", QueryAppliedDirective.class.getSimpleName() + "[", "]")
                .add("name='" + name + "'")
                .add("arguments=" + arguments)
                .add("definition=" + definition)
                .toString();
    }

    /**
     * This helps you transform the current GraphQLDirective into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new field based on calling build on that builder
     */
    public QueryAppliedDirective transform(Consumer<Builder> builderConsumer) {
        Builder builder = newDirective(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static Builder newDirective() {
        return new Builder();
    }

    public static Builder newDirective(QueryAppliedDirective existing) {
        return new Builder(existing);
    }

    @NullUnmarked
    public static class Builder extends GraphqlTypeBuilder<Builder> {

        private final Map<String, QueryAppliedDirectiveArgument> arguments = new LinkedHashMap<>();
        private Directive definition;

        public Builder() {
        }

        public Builder(QueryAppliedDirective existing) {
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.arguments.putAll(getByName(existing.getArguments(), QueryAppliedDirectiveArgument::getName));
        }

        public Builder argument(QueryAppliedDirectiveArgument argument) {
            assertNotNull(argument,"argument must not be null");
            arguments.put(argument.getName(), argument);
            return this;
        }

        public Builder replaceArguments(List<QueryAppliedDirectiveArgument> arguments) {
            assertNotNull(arguments, "arguments must not be null");
            this.arguments.clear();
            for (QueryAppliedDirectiveArgument argument : arguments) {
                this.arguments.put(argument.getName(), argument);
            }
            return this;
        }

        /**
         * Take an argument builder in a function definition and apply. Can be used in a jdk8 lambda
         * e.g.:
         * <pre>
         *     {@code
         *      argument(a -> a.name("argumentName"))
         *     }
         * </pre>
         *
         * @param builderFunction a supplier for the builder impl
         *
         * @return this
         */
        public Builder argument(UnaryOperator<QueryAppliedDirectiveArgument.Builder> builderFunction) {
            QueryAppliedDirectiveArgument.Builder builder = QueryAppliedDirectiveArgument.newArgument();
            builder = builderFunction.apply(builder);
            return argument(builder);
        }

        /**
         * Same effect as the argument(GraphQLAppliedDirectiveArgument). Builder.build() is called
         * from within
         *
         * @param builder an un-built/incomplete GraphQLAppliedDirectiveArgument
         *
         * @return this
         */
        public Builder argument(QueryAppliedDirectiveArgument.Builder builder) {
            return argument(builder.build());
        }

        /**
         * This is used to clear all the arguments in the builder so far.
         *
         * @return the builder
         */
        public Builder clearArguments() {
            arguments.clear();
            return this;
        }


        public Builder definition(Directive definition) {
            this.definition = definition;
            return this;
        }

        public QueryAppliedDirective build() {
            return new QueryAppliedDirective(name, this.definition, arguments.values());
        }
    }
}
