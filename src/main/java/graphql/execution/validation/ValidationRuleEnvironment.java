package graphql.execution.validation;

import graphql.GraphQLError;
import graphql.PublicApi;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;

import java.util.Map;

@PublicApi
public interface ValidationRuleEnvironment {

    DataFetchingEnvironment getDataFetchingEnvironment();

    /**
     * @return the field that is being validated
     */
    GraphQLFieldDefinition getValidatedField();

    /**
     * @return the argument that is being validated or null if its a field level rule
     */
    GraphQLArgument getValidatedArgument();

    /**
     * @return the argument value that is being validated or null if its a field level rule
     */
    Object getValidatedArgumentValue();

    /**
     * @return the arguments that have been passed in via the graphql query
     */
    Map<String, Object> getArguments();

    /**
     * Returns true of the named argument is present
     *
     * @param name the name of the argument
     *
     * @return true of the named argument is present
     */
    boolean containsArgument(String name);

    /**
     * Returns the named argument
     *
     * @param name the name of the argument
     * @param <T>  you decide what type it is
     *
     * @return the named argument or null if its not [present
     */
    <T> T getArgument(String name);


    /**
     * This is mutable map created per field validation that can be used for cross rule coordination.
     *
     * @return a per field validation mutable map
     */
    Map<Object, Object> getPerFieldContext();

    /**
     * A help that make validation errors
     *
     * @param message the error message
     *
     * @return a new error
     */
    GraphQLError mkError(String message);
}
