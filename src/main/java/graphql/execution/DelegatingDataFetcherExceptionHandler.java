package graphql.execution;

import graphql.AssertException;
import graphql.PublicApi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import static graphql.Assert.assertNotNull;

/**
 * Delegates to a mapping of {@link Predicate} to the desired {@link DataFetcherExceptionHandler}. The
 * corresponding {@link DataFetcherExceptionHandler} of the first {@link Predicate} to match is used. If no match is
 * found, then the {@link #setDefaultHandler(DataFetcherExceptionHandler)} is used.
 */
@PublicApi
public class DelegatingDataFetcherExceptionHandler implements DataFetcherExceptionHandler {

    private final LinkedHashMap<Predicate<Throwable>, DataFetcherExceptionHandler> delegates;

    private DataFetcherExceptionHandler defaultHandler = new SimpleDataFetcherExceptionHandler();

    /**
     * Creates a new instance
     *
     * @param delegates the mapping of {@link Predicate} to {@link DataFetcherExceptionHandler}.
     */
    public DelegatingDataFetcherExceptionHandler(LinkedHashMap<Predicate<Throwable>, DataFetcherExceptionHandler> delegates) {
        if (delegates == null || delegates.isEmpty()) {
            throw new AssertException("handlers can't be empty");
        }
        this.delegates = delegates;
    }

    @Override
    public DataFetcherExceptionHandlerResult onException(DataFetcherExceptionHandlerParameters handlerParameters) {
        Throwable exception = handlerParameters.getException();
        return delegates.entrySet().stream()
                .filter(e -> e.getKey().test(exception))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(defaultHandler)
                .onException(handlerParameters);
    }

    /**
     * Overrides the default {@link DataFetcherExceptionHandler} to be used if no matches are found. The default is
     * {@link SimpleDataFetcherExceptionHandler}.
     *
     * @param defaultHandler the {@link DataFetcherExceptionHandler} to use.
     */
    public void setDefaultHandler(DataFetcherExceptionHandler defaultHandler) {
        this.defaultHandler = assertNotNull(defaultHandler, () -> "defaultHandler can't be null");
    }
}
