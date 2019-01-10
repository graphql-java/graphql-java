package graphql.execution;

import graphql.GraphQLError;
import graphql.PublicApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static graphql.Assert.assertNotNull;

/**
 * The result object for {@link graphql.execution.DataFetcherExceptionHandler}s
 */
@PublicApi
public class DataFetcherExceptionHandlerResult {

    private final List<GraphQLError> errors;

    private DataFetcherExceptionHandlerResult(Builder builder) {
        this.errors = builder.errors;
    }

    public List<GraphQLError> getErrors() {
        return errors;
    }

    public static Builder newResult() {
        return new Builder();
    }

    public static class Builder {

        private final List<GraphQLError> errors = new ArrayList<>();

        public Builder errors(List<GraphQLError> errors) {
            this.errors.addAll(assertNotNull(errors));
            return this;
        }

        public Builder error(GraphQLError error) {
            return errors(Collections.singletonList(assertNotNull(error)));
        }

        public DataFetcherExceptionHandlerResult build() {
            return new DataFetcherExceptionHandlerResult(this);
        }

    }

}
