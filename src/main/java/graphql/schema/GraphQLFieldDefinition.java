package graphql.schema;


import graphql.DirectivesUtil;
import graphql.Internal;
import graphql.PublicApi;
import graphql.language.FieldDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static graphql.schema.DataFetcherFactoryEnvironment.newDataFetchingFactoryEnvironment;

/**
 * Fields are the ways you get data values in graphql and a field definition represents a field, its type, the arguments it takes
 * and the {@link graphql.schema.DataFetcher} used to get data values for that field.
 *
 * Fields can be thought of as functions in graphql, they have a name, take defined arguments and return a value.
 *
 * Fields can also be deprecated, which indicates the consumers that a field wont be supported in the future.
 *
 * See http://graphql.org/learn/queries/#fields for more details on the concept.
 */
@PublicApi
public class GraphQLFieldDefinition {

    private final String name;
    private final String description;
    private GraphQLOutputType type;
    private final DataFetcherFactory dataFetcherFactory;
    private final String deprecationReason;
    private final List<GraphQLArgument> arguments;
    private final List<GraphQLDirective> directives;
    private final FieldDefinition definition;


    @Deprecated
    @Internal
    public GraphQLFieldDefinition(String name, String description, GraphQLOutputType type, DataFetcher<?> dataFetcher, List<GraphQLArgument> arguments, String deprecationReason) {
        this(name, description, type, DataFetcherFactories.useDataFetcher(dataFetcher), arguments, deprecationReason, Collections.emptyList(), null);
    }

    @Internal
    public GraphQLFieldDefinition(String name, String description, GraphQLOutputType type, DataFetcherFactory dataFetcherFactory, List<GraphQLArgument> arguments, String deprecationReason, List<GraphQLDirective> directives, FieldDefinition definition) {
        this.directives = directives;
        assertValidName(name);
        assertNotNull(dataFetcherFactory, "you have to provide a DataFetcher (or DataFetcherFactory)");
        assertNotNull(type, "type can't be null");
        assertNotNull(arguments, "arguments can't be null");
        this.name = name;
        this.description = description;
        this.type = type;
        this.dataFetcherFactory = dataFetcherFactory;
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
        this.deprecationReason = deprecationReason;
        this.definition = definition;
    }


    void replaceTypeReferences(Map<String, GraphQLType> typeMap) {
        this.type = (GraphQLOutputType) new SchemaUtil().resolveTypeReference(this.type, typeMap);
    }

    public String getName() {
        return name;
    }


    public GraphQLOutputType getType() {
        return type;
    }

    public DataFetcher getDataFetcher() {
        return dataFetcherFactory.get(newDataFetchingFactoryEnvironment()
                .fieldDefinition(this)
                .build());
    }

    public GraphQLArgument getArgument(String name) {
        for (GraphQLArgument argument : arguments) {
            if (argument.getName().equals(name)) return argument;
        }
        return null;
    }

    public List<GraphQLDirective> getDirectives() {
        return Collections.unmodifiableList(directives);
    }

    public Map<String, GraphQLDirective> getDirectivesByName() {
        return DirectivesUtil.directivesByName(directives);
    }

    public GraphQLDirective getDirective(String directiveName) {
        return getDirectivesByName().get(directiveName);
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
                ", type=" + type +
                ", arguments=" + arguments +
                ", dataFetcherFactory=" + dataFetcherFactory +
                ", description='" + description + '\'' +
                ", deprecationReason='" + deprecationReason + '\'' +
                ", definition=" + definition +
                '}';
    }

    public static Builder newFieldDefinition() {
        return new Builder();
    }

    @PublicApi
    public static class Builder {

        private String name;
        private String description;
        private GraphQLOutputType type;
        private DataFetcherFactory<?> dataFetcherFactory;
        private final List<GraphQLArgument> arguments = new ArrayList<>();
        private final List<GraphQLDirective> directives = new ArrayList<>();
        private String deprecationReason;
        private boolean isField;
        private FieldDefinition definition;


        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder definition(FieldDefinition definition) {
            this.definition = definition;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
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
         */
        public Builder dataFetcher(DataFetcher<?> dataFetcher) {
            assertNotNull(dataFetcher, "dataFetcher must be not null");
            this.dataFetcherFactory = DataFetcherFactories.useDataFetcher(dataFetcher);
            return this;
        }

        /**
         * Sets the {@link graphql.schema.DataFetcherFactory} to use with this field.
         *
         * @param dataFetcherFactory the factory to use
         *
         * @return this builder
         */
        public Builder dataFetcherFactory(DataFetcherFactory dataFetcherFactory) {
            assertNotNull(dataFetcherFactory, "dataFetcherFactory must be not null");
            this.dataFetcherFactory = dataFetcherFactory;
            return this;
        }

        /**
         * This will cause the data fetcher of this field to always return the supplied value
         *
         * @param value the value to always return
         *
         * @return this builder
         */
        public Builder staticValue(final Object value) {
            this.dataFetcherFactory = DataFetcherFactories.useDataFetcher(environment -> value);
            return this;
        }

        /**
         * Get the data from a field, rather than a property.
         *
         * @return this builder
         */
        public Builder fetchField() {
            this.isField = true;
            return this;
        }

        public Builder argument(GraphQLArgument argument) {
            this.arguments.add(argument);
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
            this.arguments.add(builder.build());
            return this;
        }

        public Builder argument(List<GraphQLArgument> arguments) {
            this.arguments.addAll(arguments);
            return this;
        }

        public Builder deprecate(String deprecationReason) {
            this.deprecationReason = deprecationReason;
            return this;
        }

        public Builder withDirectives(GraphQLDirective... directives) {
            Collections.addAll(this.directives, directives);
            return this;
        }

        public GraphQLFieldDefinition build() {
            if (dataFetcherFactory == null) {
                if (isField) {
                    dataFetcherFactory = DataFetcherFactories.useDataFetcher(new FieldDataFetcher<>(name));
                } else {
                    dataFetcherFactory = DataFetcherFactories.useDataFetcher(new PropertyDataFetcher<>(name));
                }
            }
            return new GraphQLFieldDefinition(name, description, type, dataFetcherFactory, arguments, deprecationReason, directives, definition);
        }
    }
}
