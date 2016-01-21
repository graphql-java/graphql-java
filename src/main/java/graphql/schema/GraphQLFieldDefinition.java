package graphql.schema;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static graphql.Assert.assertNotNull;

public class GraphQLFieldDefinition {

    private final String name;
    private final String description;
    private GraphQLOutputType type;
    private Callable<GraphQLOutputType> typeReturningFunction;
    private final DataFetcher dataFetcher;
    private final String deprecationReason;
    private final List<GraphQLArgument> arguments = new ArrayList<>();


    public GraphQLFieldDefinition(String name, String description, GraphQLOutputType type, Callable<GraphQLOutputType> typeReturningFunction, DataFetcher dataFetcher, List<GraphQLArgument> arguments, String deprecationReason) {
        assertNotNull(name, "name can't be null");
        assertNotNull(dataFetcher, "dataFetcher can't be null");
        assertNotNull(type, "type can't be null");
        assertNotNull(arguments, "arguments can't be null");
        this.name = name;
        this.description = description;
        this.type = type;
        this.typeReturningFunction = typeReturningFunction;
        this.dataFetcher = dataFetcher;
        this.arguments.addAll(arguments);
        this.deprecationReason = deprecationReason;
    }


    void replaceTypeReferences(Map<String, GraphQLType> typeMap) {
        type = (GraphQLOutputType) new SchemaUtil().resolveTypeReference(type, typeMap);
    }

    public String getName() {
        return name;
    }


    public GraphQLOutputType getType() {
        if(type != null) {
            return type;
        }
        if(typeReturningFunction != null) {
            return typeReturningFunction.call();
        }
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

        private String name;
        private String description;
        private GraphQLOutputType type;
        private Callable<GraphQLOutputType> typeReturningFunction;
        private DataFetcher dataFetcher;
        private List<GraphQLArgument> arguments = new ArrayList<>();
        private String deprecationReason;
        private boolean isField;


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
        
        public Builder type(Callable<GraphQLOutputType> typeReturningFunction) {
            this.typeReturningFunction = typeReturningFunction;
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

        /**
         * Get the data from a field.
         * @return
         */
        public Builder fetchField() {
            this.isField = true;
            return this;
        }

        public Builder argument(GraphQLArgument argument) {
            this.arguments.add(argument);
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
            return new GraphQLFieldDefinition(name, description, type, typeReturningFunction, dataFetcher, arguments, deprecationReason);
        }


    }
}
