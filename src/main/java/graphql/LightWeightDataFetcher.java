package graphql;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;

import java.util.function.Supplier;

/**
 * A LightWeightDataFetcher is one that is only passed a minimal amount of parameter setup for fetching values
 * and hence the cost of invoking it is more light weight.
 * <p>
 * Note the engine will invoke the LightWeightDataFetcher via its {@link #get(GraphQLFieldDefinition, Object, Supplier)}
 * method and not the other {@link #get(DataFetchingEnvironment)} method.
 * <p>
 * This class derives from {@link graphql.schema.DataFetcher} so that classes like {@link graphql.schema.GraphQLCodeRegistry}
 * can still record data fetchers to coordinates and so on.
 */
@PublicSpi
public interface LightWeightDataFetcher<T> extends TrivialDataFetcher<T> {

    /**
     * This is called to by the engine to get a value from the source object
     *
     * @param executionContext the execution context
     * @param fieldDefinition  the graphql field definition
     * @param sourceObject     the source object to get a value from
     *
     * @return a value of type T. May be wrapped in a {@link graphql.execution.DataFetcherResult}
     *
     * @throws Exception to relieve the implementations from having to wrap checked exceptions. Any exception thrown
     *                   from a {@code LightWeightDataFetcher} will eventually be handled by the registered {@link graphql.execution.DataFetcherExceptionHandler}
     *                   and the related field will have a value of {@code null} in the result.
     */
    T get(GraphQLFieldDefinition fieldDefinition, Object sourceObject, Supplier<DataFetchingEnvironment> environmentSupplier) throws Exception;
}
