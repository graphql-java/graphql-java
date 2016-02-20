package graphql.schema;


/**
 * <p>StaticDataFetcher class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class StaticDataFetcher implements DataFetcher {


    private final Object value;

    /**
     * <p>Constructor for StaticDataFetcher.</p>
     *
     * @param value a {@link java.lang.Object} object.
     */
    public StaticDataFetcher(Object value) {
        this.value = value;
    }

    /** {@inheritDoc} */
    @Override
    public Object get(DataFetchingEnvironment environment) {
        return value;
    }
}
