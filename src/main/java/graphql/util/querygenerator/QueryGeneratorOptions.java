package graphql.util.querygenerator;

import graphql.schema.GraphQLSchema;

public class QueryGeneratorOptions {
    private final GraphQLSchema schema;
    private final int maxDepth;

    public QueryGeneratorOptions(GraphQLSchema schema, int maxDepth) {
        this.schema = schema;
        this.maxDepth = maxDepth;
    }

    public GraphQLSchema getSchema() {
        return schema;
    }

    public int getMaxDepth() {
        return maxDepth;
    }


    public static class QueryGeneratorOptionsBuilder {
        private int maxDepth;
        private GraphQLSchema schema;

        QueryGeneratorOptionsBuilder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        QueryGeneratorOptionsBuilder schema(GraphQLSchema schema) {
            this.schema = schema;
            return this;
        }

        public QueryGeneratorOptions build() {
            if (schema == null) {
                throw new IllegalArgumentException("Schema cannot be null");
            }

            return new QueryGeneratorOptions(
                    schema,
                    maxDepth
            );
        }
    }
}
