package graphql.schema;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GraphQLFieldDefinition {

    private final String name;
    private final String description;
    private final GraphQLOutputType type;
    private final DataFetcher dataFetcher;
    private final String deprecationReason;
    private final List<GraphQLFieldArgument> arguments = new ArrayList<>();


    public GraphQLFieldDefinition(String name, String description, GraphQLOutputType type, DataFetcher dataFetcher, List<GraphQLFieldArgument> arguments, String deprecationReason) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.dataFetcher = dataFetcher;
        if (arguments != null) {
            this.arguments.addAll(arguments);
        }
        this.deprecationReason = deprecationReason;
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

    public List<GraphQLFieldArgument> getArguments() {
        return new ArrayList<>(arguments);
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

    public static Builder newFieldDefinition() {
        return new Builder();
    }

    public static class Builder {
        private DataFetcher defaultResolver = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                return ((Map<String, Object>) environment.getSource()).get(Builder.this.name);
            }
        };

        private String name;
        private String description;
        private GraphQLOutputType type;
        private DataFetcher dataFetcher = defaultResolver;
        private List<GraphQLFieldArgument> arguments = new ArrayList<>();
        private String deprecationReason;


        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder type(GraphQLOutputType type) {
            this.type = type;
            return this;
        }

        public Builder dataFetcher(DataFetcher dataFetcher) {
            this.dataFetcher = dataFetcher;
            return this;
        }

        public Builder staticValue(final Object value) {
            this.dataFetcher = new DataFetcher() {
                @Override
                public Object get(DataFetchingEnvironment environment) {
                    return value;
                }
            };
            return this;
        }

        public Builder argument(GraphQLFieldArgument argument) {
            this.arguments.add(argument);
            return this;
        }

        public Builder argument(List<GraphQLFieldArgument> arguments) {
            this.arguments.addAll(arguments);
            return this;
        }

        public Builder deprecate(String deprecationReason) {
            this.deprecationReason = deprecationReason;
            return this;
        }

        public GraphQLFieldDefinition build() {
            return new GraphQLFieldDefinition(name, description, type, dataFetcher, arguments, deprecationReason);
        }


    }
}
