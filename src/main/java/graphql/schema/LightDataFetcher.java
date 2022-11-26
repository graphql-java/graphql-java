package graphql.schema;

import graphql.TrivialDataFetcher;

import java.util.function.Supplier;

/**
 * A {@link LightDataFetcher} is a specialised version of {@link DataFetcher} that is passed more lightweight arguments
 * when it is asked to fetch values.  The most common example of this is the {@link PropertyDataFetcher} which does not need
 * all the {@link DataFetchingEnvironment} values to perform its duties.
 *
 * @param <T> for two
 */
public interface LightDataFetcher<T> extends TrivialDataFetcher<T> {

    /**
     * This is called to by the engine to get a value from the source object in a lightweight fashion.  Only the field
     * and source object are passed in a materialised way.  The more heavy weight {@link DataFetchingEnvironment} is wrapped
     * in a supplier that is only created on demand.
     * <p>
     * If you are a lightweight data fetcher (like {@link PropertyDataFetcher} is) then you can implement this method to have a more lightweight
     * method invocation.  However, if you need field arguments etc. during fetching (most custom fetchers will) then you should use implement
     * {@link #get(DataFetchingEnvironment)}.
     *
     * @param fieldDefinition     the graphql field definition
     * @param sourceObject        the source object to get a value from
     * @param environmentSupplier a supplier of the {@link DataFetchingEnvironment} that creates it lazily
     *
     * @return a value of type T. May be wrapped in a {@link graphql.execution.DataFetcherResult}
     *
     * @throws Exception to relieve the implementations from having to wrap checked exceptions. Any exception thrown
     *                   from a {@code DataFetcher} will eventually be handled by the registered {@link graphql.execution.DataFetcherExceptionHandler}
     *                   and the related field will have a value of {@code null} in the result.
     */
    T get(GraphQLFieldDefinition fieldDefinition, Object sourceObject, Supplier<DataFetchingEnvironment> environmentSupplier) throws Exception;
}
