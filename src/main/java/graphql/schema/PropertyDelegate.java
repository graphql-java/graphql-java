package graphql.schema;

/**
 * It is some times useful to return an object from a {@link graphql.schema.DataFetcher} that is in fact a delegate.  This allows
 * you to return a more complex object that wraps the graphql properties along with other control metadata and code.
 *
 * If a returned object implements {@link graphql.schema.PropertyDelegate} then the {@link graphql.schema.PropertyDataFetcher} will
 * use the delegate as the source of properties but the original object will be the `source` for deeper graphql calls.
 *
 * @param <T> the type of delegate object
 */
public interface PropertyDelegate<T> {

    /**
     * @return a delegate object for properties
     */
    T getDelegate();
}
