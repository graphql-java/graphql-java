package graphql.schema;

/**
 * This is passed to a {@link graphql.schema.DataFetcherFactory} when it is invoked to
 * get a {@link graphql.schema.DataFetcher}
 */
public class DataFetchingFactoryEnvironment {
    private final GraphQLFieldDefinition fieldDefinition;

    DataFetchingFactoryEnvironment(GraphQLFieldDefinition fieldDefinition) {
        this.fieldDefinition = fieldDefinition;
    }

    public static Builder newDataFetchingFactoryEnvironment() {
        return new Builder();
    }

    static class Builder {
        GraphQLFieldDefinition fieldDefinition;

        public Builder fieldDefinition(GraphQLFieldDefinition fieldDefinition) {
            this.fieldDefinition = fieldDefinition;
            return this;
        }

        public DataFetchingFactoryEnvironment build() {
            return new  DataFetchingFactoryEnvironment(fieldDefinition);
        }
    }
}
