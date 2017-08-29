package graphql.schema;


import graphql.Internal;
import graphql.PublicApi;
import graphql.language.FieldDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;

@PublicApi
public class GraphQLFieldDefinition {

    private final String name;
    private final String description;
    private GraphQLOutputType type;
    private final DataFetcher dataFetcher;
    private final String deprecationReason;
    private final List<GraphQLArgument> arguments = new ArrayList<>();
    private final FieldDefinition definition;


    @Internal
    public GraphQLFieldDefinition(String name, String description, GraphQLOutputType type, DataFetcher dataFetcher, List<GraphQLArgument> arguments, String deprecationReason) {
        this(name,description,type, dataFetcher,arguments,deprecationReason,null);
    }

    @Internal
    public GraphQLFieldDefinition(String name, String description, GraphQLOutputType type, DataFetcher dataFetcher, List<GraphQLArgument> arguments, String deprecationReason, FieldDefinition definition) {
        assertValidName(name);
        assertNotNull(dataFetcher, "dataFetcher can't be null");
        assertNotNull(type, "type can't be null");
        assertNotNull(arguments, "arguments can't be null");
        this.name = name;
        this.description = description;
        this.type = type;
        this.dataFetcher = dataFetcher;
        this.arguments.addAll(arguments);
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
        return dataFetcher;
    }

    public GraphQLArgument getArgument(String name) {
        for (GraphQLArgument argument : arguments) {
            if (argument.getName().equals(name)) return argument;
        }
        return null;
    }

    public List<GraphQLArgument> getArguments() {
        return new ArrayList<>(arguments);
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
                ", dataFetcher=" + dataFetcher +
                ", arguments=" + arguments +
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
        private DataFetcher dataFetcher;
        private List<GraphQLArgument> arguments = new ArrayList<>();
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

        public Builder dataFetcher(DataFetcher dataFetcher) {
            assertNotNull(dataFetcher, "dataFetcher must be not null");
            this.dataFetcher = dataFetcher;
            return this;
        }

        public Builder staticValue(final Object value) {
            this.dataFetcher = environment -> value;
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

        public GraphQLFieldDefinition build() {
            if (dataFetcher == null) {
                if (isField) {
                    dataFetcher = new FieldDataFetcher(name);
                } else {
                    dataFetcher = new PropertyDataFetcher(name);
                }
            }
            return new GraphQLFieldDefinition(name, description, type, dataFetcher, arguments, deprecationReason, definition);
        }


    }
}
