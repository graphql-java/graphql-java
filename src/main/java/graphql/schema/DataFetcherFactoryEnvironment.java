package graphql.schema;

import graphql.PublicApi;

/**
 * This is passed to a {@link graphql.schema.DataFetcherFactory} when it is invoked to
 * get a {@link graphql.schema.DataFetcher}
 */
@PublicApi
public class DataFetcherFactoryEnvironment {
    private final GraphQLFieldDefinition fieldDefinition;
    private final GraphQLSchema graphQLSchema;

    DataFetcherFactoryEnvironment(GraphQLFieldDefinition fieldDefinition, GraphQLSchema graphQLSchema) {
        this.fieldDefinition = fieldDefinition;
        this.graphQLSchema = graphQLSchema;
    }

    /**
     * @return the field that needs a {@link graphql.schema.DataFetcher}
     */
    public GraphQLFieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    public GraphQLSchema getGraphQLSchema() {
        return graphQLSchema;
    }

    public static Builder newDataFetchingFactoryEnvironment() {
        return new Builder();
    }

    static class Builder {
        GraphQLFieldDefinition fieldDefinition;
        GraphQLSchema schema;

        public Builder fieldDefinition(GraphQLFieldDefinition fieldDefinition) {
            this.fieldDefinition = fieldDefinition;
            return this;
        }

        public Builder schema(GraphQLSchema schema) {
            this.schema = schema;
            return this;
        }

        public DataFetcherFactoryEnvironment build() {
            return new DataFetcherFactoryEnvironment(fieldDefinition, schema);
        }
    }
}
