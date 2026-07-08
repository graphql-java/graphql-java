package graphql.schema;


import graphql.PublicApi;
import graphql.TrivialDataFetcher;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A {@link graphql.schema.DataFetcher} that always returns the same value
 */
@PublicApi
@NullMarked
public class StaticDataFetcher implements DataFetcher, TrivialDataFetcher {


    private final @Nullable Object value;

    public StaticDataFetcher(@Nullable Object value) {
        this.value = value;
    }

    @Override
    public @Nullable Object get(DataFetchingEnvironment environment) {
        return value;
    }

}
