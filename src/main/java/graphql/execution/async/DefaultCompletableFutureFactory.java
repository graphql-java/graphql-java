package graphql.execution.async;

import java.util.concurrent.CompletableFuture;

public class DefaultCompletableFutureFactory implements CompletableFutureFactory {

  public static CompletableFutureFactory defaultFactory() {
    return new DefaultCompletableFutureFactory();
  }

  @Override
  public <T> CompletableFuture<T> future() {
    return new CompletableFuture<>();
  }
}
