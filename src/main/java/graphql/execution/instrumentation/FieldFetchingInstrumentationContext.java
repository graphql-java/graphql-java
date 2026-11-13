package graphql.execution.instrumentation;

import graphql.Internal;
import graphql.PublicSpi;
import graphql.execution.DataFetcherResult;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * FieldFetchingInstrumentationContext is returned back from the {@link Instrumentation#beginFieldFetching(InstrumentationFieldFetchParameters, InstrumentationState)}
 * method, and it's much like the normal {@link InstrumentationContext} type except it also
 * gives the value that was returned by a fields {@link graphql.schema.DataFetcher}.  This allows
 * you to know if the field value is a completely materialised field or if it's a {@link java.util.concurrent.CompletableFuture}
 * promise to a value.
 */
@NullMarked
@PublicSpi
public interface FieldFetchingInstrumentationContext extends InstrumentationContext<Object> {

    /**
     * This is called back with the value fetched for the field by its {@link graphql.schema.DataFetcher}.
     * This can be a materialised java object or it maybe a {@link java.util.concurrent.CompletableFuture}
     * promise to some async value that has not yet completed.
     *
     * @param fetchedValue a value that a field's {@link graphql.schema.DataFetcher} returned
     */
    default void onFetchedValue(@Nullable Object fetchedValue) {
    }

    /**
     * This is called back after any {@link graphql.execution.DataFetcherExceptionHandler}) has run on any exception raised
     * during a {@link graphql.schema.DataFetcher} invocation. This allows to see the final {@link DataFetcherResult}
     * that will be used when performing the complete step.
     * @param dataFetcherResult the final {@link DataFetcherResult} after the exception handler has run
     */
    default void onExceptionHandled(DataFetcherResult<Object> dataFetcherResult) {
    }

    @Internal
    FieldFetchingInstrumentationContext NOOP = new FieldFetchingInstrumentationContext() {
        @Override
        public void onDispatched() {
        }

        @Override
        public void onCompleted(@Nullable Object result, @Nullable Throwable t) {
        }
    };

    /**
     * This creates a no-op {@link InstrumentationContext} if the one passed in is null
     *
     * @param nullableContext a {@link InstrumentationContext} that can be null
     * @return a non-null {@link InstrumentationContext} that maybe a no-op
     */
    @Internal
    static FieldFetchingInstrumentationContext nonNullCtx(@Nullable FieldFetchingInstrumentationContext nullableContext) {
        return nullableContext == null ? NOOP : nullableContext;
    }

    @Internal
    static @Nullable FieldFetchingInstrumentationContext adapter(@Nullable InstrumentationContext<Object> context) {
        if (context == null) {
            return null;
        }
        return new FieldFetchingInstrumentationContext() {
            @Override
            public void onDispatched() {
                context.onDispatched();
            }

            @Override
            public void onCompleted(@Nullable Object result, @Nullable Throwable t) {
                context.onCompleted(result, t);
            }
        };
    }
}
