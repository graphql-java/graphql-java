package graphql.execution.instrumentation.fieldvalidation;

import graphql.GraphQLError;
import graphql.PublicApi;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionPath;

import java.util.Map;

/**
 * This contains all of the field and their arguments for a given query.  The method
 * {@link #getFields()} will be where most of the useful validation information is
 * contained.  It also gives you a helper to make validation error messages.
 *
 * @see FieldAndArguments
 */
@PublicApi
public interface FieldValidationEnvironment {

    /**
     * @return the schema in play
     */
    ExecutionContext getExecutionContext();

    /**
     * @return a map of field paths to {@link FieldAndArguments}
     */
    Map<ExecutionPath, FieldAndArguments> getFields();

    /**
     * This helper method allows you to make error messages to be passed back out in case of validation failure.  Note you
     * don't NOT have to use this helper.  Any implementation of {@link graphql.GraphQLError} is valid
     *
     * @param msg the error message
     *
     * @return a graphql error
     */
    GraphQLError mkError(String msg);

    /**
     * This helper method allows you to make error messages to be passed back out in case of validation failure.  Note you
     * don't NOT have to use this helper.  Any implementation of {@link graphql.GraphQLError} is valid
     *
     * @param msg               the error message
     * @param fieldAndArguments the field in error
     *
     * @return a graphql error
     */
    GraphQLError mkError(String msg, FieldAndArguments fieldAndArguments);
}
