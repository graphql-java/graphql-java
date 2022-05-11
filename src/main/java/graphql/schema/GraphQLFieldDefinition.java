package graphql.schema;


import com.google.common.collect.ImmutableList;
import graphql.DirectivesUtil;
import graphql.Internal;
import graphql.PublicApi;
import graphql.language.FieldDefinition;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static graphql.schema.DataFetcherFactoryEnvironment.newDataFetchingFactoryEnvironment;
import static graphql.util.FpKit.getByName;

/**
 * Fields are the ways you get data values in graphql and a field definition represents a field, its type, the arguments it takes
 * and the {@link graphql.schema.DataFetcher} used to get data values for that field.
 * <p>
 * Fields can be thought of as functions in graphql, they have a name, take defined arguments and return a value.
 * <p>
 * Fields can also be deprecated, which indicates the consumers that a field wont be supported in the future.
 * <p>
 * See http://graphql.org/learn/queries/#fields for more details on the concept.
 */
@PublicApi
public class GraphQLFieldDefinition implements GraphQLNamedSchemaElement, GraphQLDirectiveContainer {

    private final String name;
    private final String description;
    private final GraphQLOutputType originalType;
    private final DataFetcherFactory dataFetcherFactory;
    private final String deprecationReason;
    private final ImmutableList<GraphQLArgument> arguments;
    private final DirectivesUtil.DirectivesHolder directivesHolder;
    private final FieldDefinition definition;

    private GraphQLOutputType replacedType;

    public static final String CHILD_ARGUMENTS = "arguments";
    public static final String CHILD_TYPE = "type";

    @Internal
    private GraphQLFieldDefinition(String name,
                                   String description,
                                   GraphQLOutputType type,
                                   DataFetcherFactory dataFetcherFactory,
                                   List<GraphQLArgument> arguments,
                                   String deprecationReason,
                                   List<GraphQLDirective> directives,
                                   List<GraphQLAppliedDirective> appliedDirectives,
                                   FieldDefinition definition) {
        assertValidName(name);
        assertNotNull(type, () -> "type can't be null");
        assertNotNull(arguments, () -> "arguments can't be null");
        this.name = name;
        this.description = description;
        this.originalType = type;
        this.dataFetcherFactory = dataFetcherFactory;
        this.arguments = ImmutableList.copyOf(arguments);
        this.directivesHolder = new DirectivesUtil.DirectivesHolder(directives, appliedDirectives);
        this.deprecationReason = deprecationReason;
        this.definition = definition;
    }

    void replaceType(GraphQLOutputType type) {
        this.replacedType = type;
    }

    @Override
    public String getName() {
        return name;
    }


    public GraphQLOutputType getType() {
        return replacedType != null ? replacedType : originalType;
    }

    // to be removed in a future version when all code is in the code registry
    DataFetcher<?> getDataFetcher() {
        if (dataFetcherFactory == null) {
            return null;
        }
        return dataFetcherFactory.get(newDataFetchingFactoryEnvironment()
                .fieldDefinition(this)
                .build());
    }

    public GraphQLArgument getArgument(String name) {
        for (GraphQLArgument argument : arguments) {
            if (argument.getName().equals(name)) {
                return argument;
            }
        }
        return null;
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

    public List<GraphQLArgument> getArguments() {
        return arguments;
    }

    public String getDescription() {
        return description;
    }

    public FieldDefinition getDefinition() {
        return definition;
    }

    public String getDeprecationReason() {
        return deprecationReason;
    }

    public boolean isDeprecated() {
        return deprecationReason != null;
    }

    @Override
    public String toString() {
        return "GraphQLFieldDefinition{" +
                "name='" + name + '\'' +
                ", type=" + getType() +
                ", arguments=" + arguments +
                ", dataFetcherFactory=" + dataFetcherFactory +
                ", description='" + description + '\'' +
                ", deprecationReason='" + deprecationReason + '\'' +
                ", definition=" + definition +
                '}';
    }

    /**
     * This helps you transform the current GraphQLFieldDefinition into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new field based on calling build on that builder
     */
    public GraphQLFieldDefinition transform(Consumer<Builder> builderConsumer) {
        Builder builder = newFieldDefinition(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @Override
    public GraphQLSchemaElement copy() {
        return newFieldDefinition(this).build();
    }


    @Override
    public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
        return visitor.visitGraphQLFieldDefinition(this, context);
    }

    @Override
    public List<GraphQLSchemaElement> getChildren() {
        List<GraphQLSchemaElement> children = new ArrayList<>();
        children.add(getType());
        children.addAll(arguments);
        children.addAll(directivesHolder.getDirectives());
        children.addAll(directivesHolder.getAppliedDirectives());
        return children;
    }

    @Override
    public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
        return SchemaElementChildrenContainer.newSchemaElementChildrenContainer()
                .child(CHILD_TYPE, originalType)
                .children(CHILD_ARGUMENTS, arguments)
                .children(CHILD_DIRECTIVES, directivesHolder.getDirectives())
                .children(CHILD_APPLIED_DIRECTIVES, directivesHolder.getAppliedDirectives())
                .build();
    }

    // Spock mocking fails with the real return type GraphQLFieldDefinition
    @Override
    public GraphQLSchemaElement withNewChildren(SchemaElementChildrenContainer newChildren) {
        return transform(builder ->
                builder.replaceArguments(newChildren.getChildren(CHILD_ARGUMENTS))
                        .type((GraphQLOutputType) newChildren.getChildOrNull(CHILD_TYPE))
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

    public static Builder newFieldDefinition(GraphQLFieldDefinition existing) {
        return new Builder(existing);
    }

    public static Builder newFieldDefinition() {
        return new Builder();
    }

    @PublicApi
    public static class Builder extends GraphqlDirectivesContainerTypeBuilder<Builder,Builder> {

        private GraphQLOutputType type;
        private DataFetcherFactory<?> dataFetcherFactory;
        private String deprecationReason;
        private FieldDefinition definition;
        private final Map<String, GraphQLArgument> arguments = new LinkedHashMap<>();

        public Builder() {
        }

        public Builder(GraphQLFieldDefinition existing) {
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.type = existing.originalType;
            this.dataFetcherFactory = DataFetcherFactories.useDataFetcher(existing.getDataFetcher());
            this.deprecationReason = existing.getDeprecationReason();
            this.definition = existing.getDefinition();
            this.arguments.putAll(getByName(existing.getArguments(), GraphQLArgument::getName));
            copyExistingDirectives(existing);
        }

        public Builder definition(FieldDefinition definition) {
            this.definition = definition;
            return this;
        }

        public Builder type(GraphQLObjectType.Builder builder) {
            return type(builder.build());
        }

        public Builder type(GraphQLInterfaceType.Builder builder) {
            return type(builder.build());
        }

        public Builder type(GraphQLUnionType.Builder builder) {
            return type(builder.build());
        }

        public Builder type(GraphQLOutputType type) {
            this.type = type;
            return this;
        }

        /**
         * Sets the {@link graphql.schema.DataFetcher} to use with this field.
         *
         * @param dataFetcher the data fetcher to use
         *
         * @return this builder
         *
         * @deprecated use {@link graphql.schema.GraphQLCodeRegistry} instead
         */
        @Deprecated
        public Builder dataFetcher(DataFetcher<?> dataFetcher) {
            assertNotNull(dataFetcher, () -> "dataFetcher must be not null");
            this.dataFetcherFactory = DataFetcherFactories.useDataFetcher(dataFetcher);
            return this;
        }

        /**
         * Sets the {@link graphql.schema.DataFetcherFactory} to use with this field.
         *
         * @param dataFetcherFactory the data fetcher factory
         *
         * @return this builder
         *
         * @deprecated use {@link graphql.schema.GraphQLCodeRegistry} instead
         */
        @Deprecated
        public Builder dataFetcherFactory(DataFetcherFactory<?> dataFetcherFactory) {
            assertNotNull(dataFetcherFactory, () -> "dataFetcherFactory must be not null");
            this.dataFetcherFactory = dataFetcherFactory;
            return this;
        }

        /**
         * This will cause the data fetcher of this field to always return the supplied value
         *
         * @param value the value to always return
         *
         * @return this builder
         *
         * @deprecated use {@link graphql.schema.GraphQLCodeRegistry} instead
         */
        @Deprecated
        public Builder staticValue(final Object value) {
            this.dataFetcherFactory = DataFetcherFactories.useDataFetcher(environment -> value);
            return this;
        }

        public Builder argument(GraphQLArgument argument) {
            assertNotNull(argument, () -> "argument can't be null");
            this.arguments.put(argument.getName(), argument);
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
        public Builder argument(UnaryOperator<GraphQLArgument.Builder> builderFunction) {
            GraphQLArgument.Builder builder = GraphQLArgument.newArgument();
            builder = builderFunction.apply(builder);
            return argument(builder);
        }

        /**
         * Same effect as the argument(GraphQLArgument). Builder.build() is called
         * from within
         *
         * @param builder an un-built/incomplete GraphQLArgument
         *
         * @return this
         */
        public Builder argument(GraphQLArgument.Builder builder) {
            argument(builder.build());
            return this;
        }

        /**
         * This adds the list of arguments to the field.
         *
         * @param arguments the arguments to add
         *
         * @return this
         *
         * @deprecated This is a badly named method and is replaced by {@link #arguments(java.util.List)}
         */
        @Deprecated
        public Builder argument(List<GraphQLArgument> arguments) {
            return arguments(arguments);
        }

        /**
         * This adds the list of arguments to the field.
         *
         * @param arguments the arguments to add
         *
         * @return this
         */
        public Builder arguments(List<GraphQLArgument> arguments) {
            assertNotNull(arguments, () -> "arguments can't be null");
            for (GraphQLArgument argument : arguments) {
                argument(argument);
            }
            return this;
        }

        public Builder replaceArguments(List<GraphQLArgument> arguments) {
            assertNotNull(arguments, () -> "arguments can't be null");
            this.arguments.clear();
            for (GraphQLArgument argument : arguments) {
                argument(argument);
            }
            return this;
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


        public Builder deprecate(String deprecationReason) {
            this.deprecationReason = deprecationReason;
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

        public GraphQLFieldDefinition build() {
            return new GraphQLFieldDefinition(
                    name,
                    description,
                    type,
                    dataFetcherFactory,
                    sort(arguments, GraphQLFieldDefinition.class, GraphQLArgument.class),
                    deprecationReason,
                    sort(directives, GraphQLFieldDefinition.class, GraphQLDirective.class),
                    sort(appliedDirectives, GraphQLScalarType.class, GraphQLAppliedDirective.class),
                    definition);
        }
    }
}
