package graphql.schema;

import graphql.PublicApi;
import graphql.execution.ExecutionId;
import graphql.language.Field;
import graphql.language.FragmentDefinition;

import java.util.List;
import java.util.Map;

/**
 * A DataFetchingEnvironment instance of passed to a {@link DataFetcher} as an execution context parameter
 */
@PublicApi
public interface DataFetchingEnvironment {

    /**
     * This is the value of the current object to be queried.
     * Or to put it differently: it is the value of the parent field.
     * <p>
     * For the root query, it is equal to {{@link DataFetchingEnvironment#getRoot}
     *
     * @param <T> you decide what type it is
     * @return can be null for the root query, otherwise it is never null
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
     * @return true of the named argument is present
     */
    boolean containsArgument(String name);

    /**
     * Returns the named argument
     *
     * @param name the name of the argument
     * @param <T>  you decide what type it is
     * @return the named argument or null if its not [present
     */
    <T> T getArgument(String name);

    /**
     * Returns a context argument that is set up when the {@link graphql.GraphQL#execute} method
     * is invoked.
     * <p>
     * This is a info object which is provided to all DataFetcher, but never used by graphql-java itself.
     *
     * @param <T> you decide what type it is
     * @return can be null
     */
    <T> T getContext();

    /**
     * This is the source object for the root query.
     *
     * @param <T>  you decide what type it is
     * @return can be null
     */
    <T> T getRoot();

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

    /**
     * @return the {@link DataFetchingFieldSelectionSet} for the current operation
     */
    DataFetchingFieldSelectionSet getSelectionSet();
}
