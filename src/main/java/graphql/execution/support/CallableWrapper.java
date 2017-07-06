package graphql.execution.support;

import java.util.concurrent.Callable;

/**
 * The entry point for defining implementation-specific logic for wrapping each {@link Callable} executed
 * by the {@link graphql.execution.ExecutorServiceExecutionStrategy}.
 * <p>
 * This allows implementations to pass context (e.g. request attributes or logging MDC context) to child
 * threads for data fetching.
 * <pre>{@code
 *  public class RequestAttributeAwareCallableWrapper implements CallableWrapper {
 *
 *      public <T> Callable<T> wrapCallable(Callable<T> callable) {
 *          return new RequestAttributeAwareCallable<>(callable);
 *      }
 *  }
 *
 *  ...
 *
 *  public class RequestAttributeAwareCallable<T> implements Callable<T> {
 *
 *      private final Callable<T> callable;
 *      private final RequestAttributes requestAttributes;
 *
 *      public RequestAttributeAwareCallable(Callable<T> callable) {
 *          this(callable, RequestContextHolder.currentRequestAttributes());
 *      }
 *
 *      private RequestAttributeAwareCallable(Callable<T> callable, RequestAttributes requestAttributes) {
 *          this.callable = callable;
 *          this.requestAttributes = requestAttributes;
 *      }
 *
 *      public T call() throws Exception {
 *          try {
 *              RequestContextHolder.setRequestAttributes(requestAttributes, true);
 *              return callable.call();
 *          } finally {
 *              RequestContextHolder.resetRequestAttributes();
 *          }
 *      }
 *  }
 *
 * }</pre>
 */
public interface CallableWrapper {

    /**
     * Wraps a callable in order to pass context from the parent thread to Callable (child) thread.
     *
     * @param callable The callable to wrap
     * @param <T>      The callable result type
     * @return A wrapped callable
     */
    <T> Callable<T> wrapCallable(Callable<T> callable);
}
