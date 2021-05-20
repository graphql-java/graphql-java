package graphql.execution;

import graphql.PublicApi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotEmpty;
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
        this.delegates = assertNotEmpty(delegates, () -> "handlers can't be empty");
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

    /**
     * Creates a {@link DelegatingDataFetcherExceptionHandler} from a mapping of {@link Throwable} to
     * {@link DataFetcherExceptionHandler} such that match occurs if the
     * {@link DataFetcherExceptionHandlerParameters#getException()} contains an {@link Exception} that is the same or
     * subtype of the key.
     *
     * @param types the mapping of type to {@link DataFetcherExceptionHandler}
     *
     * @return the {@link DelegatingDataFetcherExceptionHandler} to use
     */
    public static DelegatingDataFetcherExceptionHandler fromThrowableTypeMapping(LinkedHashMap<Class<? extends Throwable>, DataFetcherExceptionHandler> types) {
        LinkedHashMap<Class<? extends Throwable>, DataFetcherExceptionHandler> typeToHandler = assertNotEmpty(types, () -> "types can't be null");
        LinkedHashMap<Predicate<Throwable>, DataFetcherExceptionHandler> handlers = typeToHandler.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        e -> (Predicate<Throwable>) throwable -> e.getKey().isAssignableFrom(throwable.getClass()),
                        e -> e.getValue(),
                        (v1, v2) -> v1,
                        LinkedHashMap::new)
                );
        return new DelegatingDataFetcherExceptionHandler(handlers);
    }
}
