package graphql.schema;

import java.util.function.Supplier;

/**
 * The {@link SingletonPropertyDataFetcher} is much like the {@link PropertyDataFetcher} except
 * that it is designed to only ever fetch properties via the name of the field passed in.
 * <p>
 * This uses the same code as {@link PropertyDataFetcher} and hence is also controlled
 * by static methods such as {@link PropertyDataFetcher#setUseNegativeCache(boolean)}
 *
 * @param <T> for two
 */
public class SingletonPropertyDataFetcher<T> implements LightDataFetcher<T> {

    private static final SingletonPropertyDataFetcher<Object> SINGLETON_FETCHER = new SingletonPropertyDataFetcher<>();

    private static final DataFetcherFactory<?> SINGLETON_FETCHER_FACTORY = environment -> SINGLETON_FETCHER;

    /**
     * This returns the same singleton {@link LightDataFetcher} that fetches property values
     * based on the name of the field that iis passed into it.
     *
     * @return a singleton property data fetcher
     */
    public static LightDataFetcher<?> singleton() {
        return SINGLETON_FETCHER;
    }

    /**
     * This returns the same singleton {@link DataFetcherFactory} that returns the value of {@link #singleton()}
     *
     * @return a singleton data fetcher factory
     */
    public static DataFetcherFactory<?> singletonFactory() {
        return SINGLETON_FETCHER_FACTORY;
    }

    private SingletonPropertyDataFetcher() {
    }

    @Override
    public T get(GraphQLFieldDefinition fieldDefinition, Object sourceObject, Supplier<DataFetchingEnvironment> environmentSupplier) throws Exception {
        return fetchImpl(fieldDefinition, sourceObject, environmentSupplier);
    }

    @Override
    public T get(DataFetchingEnvironment environment) throws Exception {
        return fetchImpl(environment.getFieldDefinition(), environment.getSource(), () -> environment);
    }

    private T fetchImpl(GraphQLFieldDefinition fieldDefinition, Object source, Supplier<DataFetchingEnvironment> environmentSupplier) {
        if (source == null) {
            return null;
        }
        // this is the same code that PropertyDataFetcher uses and hence unit tests for it include this one
        //noinspection unchecked
        return (T) PropertyDataFetcherHelper.getPropertyValue(fieldDefinition.getName(), source, fieldDefinition.getType(), environmentSupplier);
    }
}
