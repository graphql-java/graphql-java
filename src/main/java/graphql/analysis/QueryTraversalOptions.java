package graphql.analysis;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

/**
 * This options object controls how {@link QueryTraverser} works
 */
@PublicApi
@NullMarked
public class QueryTraversalOptions {

    private final boolean coerceFieldArguments;

    private QueryTraversalOptions(boolean coerceFieldArguments) {
        this.coerceFieldArguments = coerceFieldArguments;
    }

    /**
     * @return true if field arguments should be coerced.  This is true by default.
     */
    public boolean isCoerceFieldArguments() {
        return coerceFieldArguments;
    }

    public static QueryTraversalOptions defaultOptions() {
        return new QueryTraversalOptions(true);
    }

    public QueryTraversalOptions coerceFieldArguments(boolean coerceFieldArguments) {
        return new QueryTraversalOptions(coerceFieldArguments);
    }
}
