package graphql.execution.instrumentation;

import graphql.Internal;
import graphql.PublicSpi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@PublicSpi
public interface FieldFetchingInstrumentationContext extends InstrumentationContext<Object> {

    @Internal
    FieldFetchingInstrumentationContext NOOP = new FieldFetchingInstrumentationContext() {
        @Override
        public void onDispatched() {
        }

        @Override
        public void onCompleted(Object result, Throwable t) {
        }

        @Override
        public void onFetchedValue(Object fetchedValue) {
        }
    };

    /**
     * This creates a no-op {@link InstrumentationContext} if the one pass in is null
     *
     * @param nullableContext a {@link InstrumentationContext} that can be null
     *
     * @return a non null {@link InstrumentationContext} that maybe a no-op
     */
    @NotNull
    @Internal
    static FieldFetchingInstrumentationContext nonNullCtx(FieldFetchingInstrumentationContext nullableContext) {
        return nullableContext == null ? NOOP : nullableContext;
    }

    @Internal
    static FieldFetchingInstrumentationContext adapter(@Nullable InstrumentationContext<Object> context) {
        if (context == null) {
            return null;
        }
        return new FieldFetchingInstrumentationContext() {
            @Override
            public void onDispatched() {
                context.onDispatched();
            }

            @Override
            public void onCompleted(Object result, Throwable t) {
                context.onCompleted(result, t);
            }

            @Override
            public void onFetchedValue(Object fetchedValue) {
            }
        };
    }

    /**
     * This is called back with value fetched for the field.
     *
     * @param fetchedValue a value that a field's {@link graphql.schema.DataFetcher} returned
     */
    default void onFetchedValue(Object fetchedValue) {
    }
}
