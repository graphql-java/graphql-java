package graphql.execution.instrumentation;

/**
 * When a {@link Instrumentation}.'beginXXX' method is called then it must return a non null InstrumentationContext
 * that will the be invoked as {@link #onEnd(Object, java.lang.Throwable)} when the step completes.
 *
 * This pattern of construction of an object then call back is intended to allow "timers" to be created that can instrument what has
 * just happened or "loggers" to be called to record what has happened.
 */
public interface InstrumentationContext<T> {

    /**
     * This is invoked when the execution step is completed
     *
     * @param result the result of the step (which may be null)
     * @param t      this exception will be non null if an exception was thrown during the step
     */
    void onEnd(T result, Throwable t);

}
