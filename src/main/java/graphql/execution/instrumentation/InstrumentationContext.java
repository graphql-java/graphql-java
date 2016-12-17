package graphql.execution.instrumentation;

/**
 * When a {@link Instrumentation}.'beginXXX' method is called then it must return a non null InstrumentationContext
 * that will the be invoked as {@link #onEnd(Object)} or {@link #onEnd(Exception)} when the step completes.
 *
 * This pattern of construction of an object then call back is intended to allow "timers" to be created that can instrument what has
 * just happened or "loggers" to be called to record what has happened.
 */
public interface InstrumentationContext<T> {

    /**
     * This is invoked when the execution step is completed successfully
     * @param result the successful result of the step
     */
    void onEnd(T result);

    /**
     * This is invoked when the execution step is completed unsuccessfully
     * @param e the exception that happened during the step
     */
    void onEnd(Exception e);
}
