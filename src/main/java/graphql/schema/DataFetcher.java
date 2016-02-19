package graphql.schema;


/**
 * <p>DataFetcher interface.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public interface DataFetcher {

    /**
     * <p>get.</p>
     *
     * @param environment a {@link graphql.schema.DataFetchingEnvironment} object.
     * @return a {@link java.lang.Object} object.
     */
    Object get(DataFetchingEnvironment environment);
}
