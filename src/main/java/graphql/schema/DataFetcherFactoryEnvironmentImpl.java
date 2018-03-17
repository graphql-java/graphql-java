package graphql.schema;

import graphql.Internal;

@Internal
public class DataFetcherFactoryEnvironmentImpl implements DataFetcherFactoryEnvironment {
    private final GraphQLFieldDefinition fieldDefinition;
    private final GraphQLSchema graphQLSchema;

    DataFetcherFactoryEnvironmentImpl(GraphQLFieldDefinition fieldDefinition, GraphQLSchema graphQLSchema) {
        this.fieldDefinition = fieldDefinition;
        this.graphQLSchema = graphQLSchema;
    }

    public GraphQLFieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    @Override
    public GraphQLSchema getSchema() {
        return this.graphQLSchema;
    }

    public static Builder newDataFetchingFactoryEnvironment() {
        return new Builder();
    }

    public static class Builder {
        private GraphQLFieldDefinition fieldDefinition;
        private GraphQLSchema graphQLSchema;

        public Builder fieldDefinition(GraphQLFieldDefinition fieldDefinition) {
            this.fieldDefinition = fieldDefinition;
            return this;
        }

        public Builder schema(GraphQLSchema graphQLSchema) {
            this.graphQLSchema = graphQLSchema;
            return this;
        }

        public DataFetcherFactoryEnvironmentImpl build() {
            return new DataFetcherFactoryEnvironmentImpl(fieldDefinition, graphQLSchema);
        }
    }
}
