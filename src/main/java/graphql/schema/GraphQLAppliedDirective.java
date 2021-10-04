package graphql.schema;


import com.google.common.collect.ImmutableList;
import graphql.PublicApi;
import graphql.language.Directive;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

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
 * An applied directive repreents the instance of a  directive that is applied to a schema element,
 * as opposed to it definition
 * <p>
 * See http://graphql.org/learn/queries/#directives for more details on the concept.
 */
@PublicApi
public class GraphQLAppliedDirective implements GraphQLNamedSchemaElement {

    private final String name;
    private final ImmutableList<GraphQLAppliedArgument> arguments;
    private final Directive definition;

    public static final String CHILD_ARGUMENTS = "arguments";

    private GraphQLAppliedDirective(String name, Directive definition, List<GraphQLAppliedArgument> arguments) {
        assertValidName(name);
        assertNotNull(arguments, () -> "arguments can't be null");
        this.name = name;
        this.arguments = ImmutableList.copyOf(arguments);
        this.definition = definition;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return null;
    }

    public List<GraphQLAppliedArgument> getArguments() {
        return arguments;
    }

    public GraphQLAppliedArgument getArgument(String name) {
        for (GraphQLAppliedArgument argument : arguments) {
            if (argument.getName().equals(name)) {
                return argument;
            }
        }
        return null;
    }

    public Directive getDefinition() {
        return definition;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", GraphQLAppliedDirective.class.getSimpleName() + "[", "]")
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
    public GraphQLAppliedDirective transform(Consumer<Builder> builderConsumer) {
        Builder builder = newDirective(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @Override
    public GraphQLSchemaElement copy() {
        return newDirective(this).build();
    }


    @Override
    public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
        return visitor.visitGraphQLAppliedDirective(this, context);
    }

    @Override
    public List<GraphQLSchemaElement> getChildren() {
        return ImmutableList.copyOf(arguments);
    }

    @Override
    public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
        return SchemaElementChildrenContainer.newSchemaElementChildrenContainer()
                .children(CHILD_ARGUMENTS, arguments)
                .build();
    }

    @Override
    public GraphQLAppliedDirective withNewChildren(SchemaElementChildrenContainer newChildren) {
        return transform(builder ->
                builder.replaceArguments(newChildren.getChildren(CHILD_ARGUMENTS))
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


    public static Builder newDirective() {
        return new Builder();
    }

    public static Builder newDirective(GraphQLAppliedDirective existing) {
        return new Builder(existing);
    }

    public static class Builder extends GraphqlTypeBuilder {

        private final Map<String, GraphQLAppliedArgument> arguments = new LinkedHashMap<>();
        private Directive definition;

        public Builder() {
        }

        public Builder(GraphQLAppliedDirective existing) {
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.arguments.putAll(getByName(existing.getArguments(), GraphQLAppliedArgument::getName));
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

        public Builder argument(GraphQLAppliedArgument argument) {
            assertNotNull(argument, () -> "argument must not be null");
            arguments.put(argument.getName(), argument);
            return this;
        }

        public Builder replaceArguments(List<GraphQLAppliedArgument> arguments) {
            assertNotNull(arguments, () -> "arguments must not be null");
            this.arguments.clear();
            for (GraphQLAppliedArgument argument : arguments) {
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
        public Builder argument(UnaryOperator<GraphQLAppliedArgument.Builder> builderFunction) {
            GraphQLAppliedArgument.Builder builder = GraphQLAppliedArgument.newArgument();
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
        public Builder argument(GraphQLAppliedArgument.Builder builder) {
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

        public GraphQLAppliedDirective build() {
            return new GraphQLAppliedDirective(name, this.definition, sort(arguments, GraphQLAppliedDirective.class, GraphQLAppliedArgument.class));
        }
    }
}
