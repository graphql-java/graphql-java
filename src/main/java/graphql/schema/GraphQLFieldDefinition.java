package graphql.schema;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;

/**
 * <p>GraphQLFieldDefinition class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class GraphQLFieldDefinition {

    private final String name;
    private final String description;
    private GraphQLOutputType type;
    private final DataFetcher dataFetcher;
    private final String deprecationReason;
    private final List<GraphQLArgument> arguments = new ArrayList<>();


    /**
     * <p>Constructor for GraphQLFieldDefinition.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param description a {@link java.lang.String} object.
     * @param type a {@link graphql.schema.GraphQLOutputType} object.
     * @param dataFetcher a {@link graphql.schema.DataFetcher} object.
     * @param arguments a {@link java.util.List} object.
     * @param deprecationReason a {@link java.lang.String} object.
     */
    public GraphQLFieldDefinition(String name, String description, GraphQLOutputType type, DataFetcher dataFetcher, List<GraphQLArgument> arguments, String deprecationReason) {
        assertNotNull(name, "name can't be null");
        assertNotNull(dataFetcher, "dataFetcher can't be null");
        assertNotNull(type, "type can't be null");
        assertNotNull(arguments, "arguments can't be null");
        this.name = name;
        this.description = description;
        this.type = type;
        this.dataFetcher = dataFetcher;
        this.arguments.addAll(arguments);
        this.deprecationReason = deprecationReason;
    }


    void replaceTypeReferences(Map<String, GraphQLType> typeMap) {
        type = (GraphQLOutputType) new SchemaUtil().resolveTypeReference(type, typeMap);
    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getName() {
        return name;
    }


    /**
     * <p>Getter for the field <code>type</code>.</p>
     *
     * @return a {@link graphql.schema.GraphQLOutputType} object.
     */
    public GraphQLOutputType getType() {
        return type;
    }

    /**
     * <p>Getter for the field <code>dataFetcher</code>.</p>
     *
     * @return a {@link graphql.schema.DataFetcher} object.
     */
    public DataFetcher getDataFetcher() {
        return dataFetcher;
    }

    /**
     * <p>getArgument.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @return a {@link graphql.schema.GraphQLArgument} object.
     */
    public GraphQLArgument getArgument(String name) {
        for (GraphQLArgument argument : arguments) {
            if (argument.getName().equals(name)) return argument;
        }
        return null;
    }

    /**
     * <p>Getter for the field <code>arguments</code>.</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<GraphQLArgument> getArguments() {
        return new ArrayList<>(arguments);
    }

    /**
     * <p>Getter for the field <code>description</code>.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getDescription() {
        return description;
    }

    /**
     * <p>Getter for the field <code>deprecationReason</code>.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getDeprecationReason() {
        return deprecationReason;
    }

    /**
     * <p>isDeprecated.</p>
     *
     * @return a boolean.
     */
    public boolean isDeprecated() {
        return deprecationReason != null;
    }

    /**
     * <p>newFieldDefinition.</p>
     *
     * @return a {@link graphql.schema.GraphQLFieldDefinition.Builder} object.
     */
    public static Builder newFieldDefinition() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private String description;
        private GraphQLOutputType type;
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
            return new GraphQLFieldDefinition(name, description, type, dataFetcher, arguments, deprecationReason);
        }


    }
}
