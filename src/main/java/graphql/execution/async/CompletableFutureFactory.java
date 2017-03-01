package graphql.execution.async;

import java.util.concurrent.CompletableFuture;

public interface CompletableFutureFactory {
  <T> CompletableFuture<T> future();
}
