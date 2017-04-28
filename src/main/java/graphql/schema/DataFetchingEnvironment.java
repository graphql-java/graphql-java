package graphql.schema;

import graphql.execution.ExecutionId;
import graphql.language.Field;
import graphql.language.FragmentDefinition;

import java.util.List;
import java.util.Map;

/**
 * A DataFetchingEnvironment instance of passed to a {@link DataFetcher} as an execution context parameter
 */
public interface DataFetchingEnvironment {
    /**
     * @param <T> you decide what type it is
     *
     * @return the current object being queried
     */
    <T> T getSource();

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
     * Returns a context argument that is set up when the {@link graphql.GraphQL#execute(String, Object)} method
     * is invoked
     *
     * @param <T> you decide what type it is
     *
     * @return a context object
     */
    <T> T getContext();

    /**
     * @return the list of fields currently in query context
     */
    List<Field> getFields();

    /**
     * @return graphql type of the current field
     */
    GraphQLOutputType getFieldType();

    /**
     * @return the type of the parent of the current field
     */
    GraphQLType getParentType();

    /**
     * @return the underlying graphql schema
     */
    GraphQLSchema getGraphQLSchema();

    /**
     * @return the {@link FragmentDefinition} map for the current operation
     */
    Map<String, FragmentDefinition> getFragmentsByName();

    /**
     * @return the {@link ExecutionId} for the current operation
     */
    ExecutionId getExecutionId();
}
