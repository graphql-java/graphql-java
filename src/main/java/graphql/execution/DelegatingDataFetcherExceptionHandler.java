package graphql.execution;

import graphql.PublicApi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static graphql.Assert.assertNotEmpty;
import static graphql.Assert.assertNotNull;

/**
 * Delegates to a mapping of {@link Predicate} to the desired {@link DataFetcherExceptionHandler}. The
 * corresponding {@link DataFetcherExceptionHandler} of the first {@link Predicate} to match is used. If no match is
 * found, then the default is used.
 */
@PublicApi
public class DelegatingDataFetcherExceptionHandler implements DataFetcherExceptionHandler {

    private final List<PredicateDelegateMapping> delegates;

    private final DataFetcherExceptionHandler defaultHandler;

    /**
     * Creates a new instance
     *
     * @param delegates the mapping of {@link Predicate} to {@link DataFetcherExceptionHandler}.
     * @param defaultHandler the default {@link DataFetcherExceptionHandler} to use is none of the delegates match
     */
    private DelegatingDataFetcherExceptionHandler(List<PredicateDelegateMapping> delegates, DataFetcherExceptionHandler defaultHandler) {
        this.delegates = assertNotEmpty(delegates, () -> "delegates can't be empty");
        this.defaultHandler = assertNotNull(defaultHandler, () -> "defaultHandler cannot be null");
    }

    @Override
    public DataFetcherExceptionHandlerResult onException(DataFetcherExceptionHandlerParameters handlerParameters) {
        Throwable exception = handlerParameters.getException();
        return delegates.stream()
                .filter(mapping -> mapping.getPredicate().test(exception))
                .map(PredicateDelegateMapping::getDelegate)
                .findFirst()
                .orElse(defaultHandler)
                .onException(handlerParameters);
    }

    public static Builder newDelegatingDataFetcherExceptionHandler() {
        return new Builder();
    }

    @PublicApi
    public static class Builder {

        private List<PredicateDelegateMapping> delegateMappings = new ArrayList<>();

        private DataFetcherExceptionHandler defaultHandler = new SimpleDataFetcherExceptionHandler();

        /**
         * Adds a mapping of the {@link Predicate} to the {@link DataFetcherExceptionHandler}. If the {@link Predicate}
         * returns true when {@link DataFetcherExceptionHandlerParameters#getException()} is passed into it, then the
         * corresponding {@link DataFetcherExceptionHandler} will be used. Only the first match will be used.
         * @param predicate the {@link Predicate} to test if the delegate should be used
         * @param delegate the {@link DataFetcherExceptionHandler} to use if the predicate matches
         * @return the Builder for further customizations
         */
        public Builder addMapping(Predicate<Throwable> predicate, DataFetcherExceptionHandler delegate) {
            this.delegateMappings.add(new PredicateDelegateMapping(predicate, delegate));
            return this;
        }

        /**
         * Adds a mapping of the {@link Throwable} to {@link DataFetcherExceptionHandler} such that a match occurs if the
         * {@link DataFetcherExceptionHandlerParameters#getException()} contains an {@link Exception} that is the same or
         * subtype of #matchingThrowableType. Only the first match will be used.
         * @param matchingThrowableType the type to test if the Throwable is an instance of
         * @param delegate the {@link DataFetcherExceptionHandler} to use if the Throwable is an instance of matchingThrowableType
         * @return the Builder for further customizations
         */
        public Builder addMapping(Class<? extends Throwable> matchingThrowableType, DataFetcherExceptionHandler delegate) {
            assertNotNull(matchingThrowableType, () -> "matchingThrowableType cannot be null");
            Predicate<Throwable> predicate = throwable -> matchingThrowableType.isAssignableFrom(throwable.getClass());
            return addMapping(predicate, delegate);
        }

        public Builder defaultHandler(DataFetcherExceptionHandler defaultHandler) {
            this.defaultHandler = assertNotNull(defaultHandler, () -> "defaultHandler cannot be null");
            return this;
        }

        public DelegatingDataFetcherExceptionHandler build() {
            return new DelegatingDataFetcherExceptionHandler(this.delegateMappings, this.defaultHandler);
        }
    }

    /**
     * A mapping of a {@link Predicate} to {@link DataFetcherExceptionHandler}.
     */
    private static class PredicateDelegateMapping {

        private final Predicate<Throwable> predicate;

        private final DataFetcherExceptionHandler delegate;

        private PredicateDelegateMapping(Predicate<Throwable> predicate, DataFetcherExceptionHandler delegate) {
            this.predicate = assertNotNull(predicate, () -> "predicate cannot be null");
            this.delegate = assertNotNull(delegate, () -> "delegate cannot be null");
        }

        private Predicate<Throwable> getPredicate() {
            return predicate;
        }

        private DataFetcherExceptionHandler getDelegate() {
            return delegate;
        }
    }
}
