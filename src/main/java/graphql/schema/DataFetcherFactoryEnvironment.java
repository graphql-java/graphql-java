package graphql.schema;

import graphql.PublicApi;

/**
 * This is passed to a {@link graphql.schema.DataFetcherFactory} when it is invoked to
 * get a {@link graphql.schema.DataFetcher}
 *
 * @deprecated This class will go away at some point in the future since its pointless wrapper
 * of a {@link GraphQLFieldDefinition}
 */
@PublicApi
@Deprecated(since = "2024-11-26")
public class DataFetcherFactoryEnvironment {
    private final GraphQLFieldDefinition fieldDefinition;

    DataFetcherFactoryEnvironment(GraphQLFieldDefinition fieldDefinition) {
        this.fieldDefinition = fieldDefinition;
    }

    /**
     * @return the field that needs a {@link graphql.schema.DataFetcher}
     */
    public GraphQLFieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    public static Builder newDataFetchingFactoryEnvironment() {
        return new Builder();
    }

    public static class Builder {
        GraphQLFieldDefinition fieldDefinition;

        public Builder fieldDefinition(GraphQLFieldDefinition fieldDefinition) {
            this.fieldDefinition = fieldDefinition;
            return this;
        }

        public DataFetcherFactoryEnvironment build() {
            return new DataFetcherFactoryEnvironment(fieldDefinition);
        }
    }
}
