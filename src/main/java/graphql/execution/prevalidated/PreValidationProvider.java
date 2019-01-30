package graphql.execution.prevalidated;


import graphql.ExecutionInput;
import graphql.language.Document;
import graphql.schema.GraphQLSchema;
import graphql.validation.ValidationError;

import java.util.List;
import java.util.function.Supplier;

/**
 * This allows you to cache validation of queries and whitelist or blacklist known queries quickly.
 */
public interface PreValidationProvider {

    /**
     * This is called to get a "cached" validation error list and if its not present, then the validationFunction
     * can be called to actually validate the query
     *
     * @param executionInput     The {@link graphql.ExecutionInput} in play
     * @param queryDocument      The parsed graphql query document
     * @param graphQLSchema      The schema in place
     * @param validationFunction If the query has not be validated , this supplier function can be called to validate  it
     *
     * @return a list of errors from validation.  If this is not empty then query will fail validation
     */
    List<ValidationError> get(ExecutionInput executionInput, Document queryDocument, GraphQLSchema graphQLSchema, Supplier<List<ValidationError>> validationFunction);
}


