package graphql.execution.instrumentation;

import graphql.PublicApi;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * A simple implementation of {@link InstrumentationContext}
 */
@PublicApi
@NullMarked
public class SimpleInstrumentationContext<T> implements InstrumentationContext<T> {

    private static final InstrumentationContext<Object> NO_OP = new InstrumentationContext<Object>() {
        @Override
        public void onDispatched() {
        }

        @Override
        public void onCompleted(@Nullable Object result, @Nullable Throwable t) {
        }
    };

    /**
     * A context that does nothing
     *
     * @param <T> the type needed
     *
     * @return a context that does nothing
     */
    @SuppressWarnings("unchecked")
    public static <T> InstrumentationContext<T> noOp() {
        return (InstrumentationContext<T>) NO_OP;
    }

    /**
     * This creates a no-op {@link InstrumentationContext} if the one pass in is null
     *
     * @param nullableContext a {@link InstrumentationContext} that can be null
     * @param <T>             for two
     *
     * @return a non null {@link InstrumentationContext} that maybe a no-op
     */
    @NonNull
    public static <T> InstrumentationContext<T> nonNullCtx(@Nullable InstrumentationContext<T> nullableContext) {
        return nullableContext == null ? noOp() : nullableContext;
    }

    private final @Nullable BiConsumer<T, Throwable> codeToRunOnComplete;
    private final @Nullable Runnable codeToRunOnDispatch;

    public SimpleInstrumentationContext() {
        this(null, null);
    }

    private SimpleInstrumentationContext(@Nullable Runnable codeToRunOnDispatch, @Nullable BiConsumer<T, Throwable> codeToRunOnComplete) {
        this.codeToRunOnComplete = codeToRunOnComplete;
        this.codeToRunOnDispatch = codeToRunOnDispatch;
    }

    @Override
    public void onDispatched() {
        if (codeToRunOnDispatch != null) {
            codeToRunOnDispatch.run();
        }
    }

    @Override
    public void onCompleted(@Nullable T result, @Nullable Throwable t) {
        if (codeToRunOnComplete != null) {
            codeToRunOnComplete.accept(result, t);
        }
    }

    /**
     * Allows for the more fluent away to return an instrumentation context that runs the specified
     * code on instrumentation step dispatch.
     *
     * @param codeToRun the code to run on dispatch
     * @param <U>       the generic type
     *
     * @return an instrumentation context
     */
    public static <U> SimpleInstrumentationContext<U> whenDispatched(Runnable codeToRun) {
        return new SimpleInstrumentationContext<>(codeToRun, null);
    }

    /**
     * Allows for the more fluent away to return an instrumentation context that runs the specified
     * code on instrumentation step completion.
     *
     * @param codeToRun the code to run on completion
     * @param <U>       the generic type
     *
     * @return an instrumentation context
     */
    public static <U> SimpleInstrumentationContext<U> whenCompleted(BiConsumer<U, Throwable> codeToRun) {
        return new SimpleInstrumentationContext<>(null, codeToRun);
    }

    public static <T> BiConsumer<? super T, ? super Throwable> completeInstrumentationCtxCF(
            InstrumentationContext<T> instrumentationContext) {
        return (result, throwable) -> {
            nonNullCtx(instrumentationContext).onCompleted(result, throwable);
        };
    }

}
