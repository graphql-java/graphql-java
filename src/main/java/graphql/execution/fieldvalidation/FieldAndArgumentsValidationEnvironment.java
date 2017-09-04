package graphql.execution.fieldvalidation;

import graphql.GraphQLError;
import graphql.PublicApi;
import graphql.execution.ExecutionPath;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLSchema;

import java.util.Map;

/**
 * This contains all of the field and their arguments for a given query.  The method
 * {@link #getFieldArguments()} will be where most of the useful validation information is
 * contained.  It also gives you a helper to make validation error messages.
 *
 * @see FieldAndArguments
 */
@PublicApi
public interface FieldAndArgumentsValidationEnvironment {

    /**
     * @return the schema in play
     */
    GraphQLSchema getSchema();

    /**
     * @return the operation being executed
     */
    OperationDefinition getOperation();

    /**
     * @return a map of field paths to {@link FieldAndArguments}
     */
    Map<ExecutionPath, FieldAndArguments> getFieldArguments();

    /**
     * This helper method allows you to make error messages to be passed back out in case of validation failure
     *
     * @param msg   the error message
     * @param field the (optional) field in error
     * @param path  the (optional) path to the field
     *
     * @return a graphql error
     */
    GraphQLError mkError(String msg, Field field, ExecutionPath path);
}
