package graphql.execution.async;

import graphql.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static java.util.concurrent.CompletableFuture.completedFuture;

class AsyncFieldsCoordinator {

    private static final Logger log = LoggerFactory.getLogger(AsyncFieldsCoordinator.class);

    private final Map<String, Supplier<CompletionStage<ExecutionResult>>> resolvers;

    AsyncFieldsCoordinator(Map<String, Supplier<CompletionStage<ExecutionResult>>> resolvers) {
        this.resolvers = resolvers;
    }

    public CompletionStage<Map<String, Object>> executeSerially() {
        return executeSerially(new ArrayList<>(resolvers.entrySet()), new LinkedHashMap<>(), 0);
    }

    private CompletionStage<Map<String, Object>> executeSerially(List<Map.Entry<String, Supplier<CompletionStage<ExecutionResult>>>> resolvers, Map<String, Object> results, int i) {
        return resolvers.get(i).getValue().get().thenCompose(result -> {
            results.put(resolvers.get(i).getKey(), result.getData());
            if (i == resolvers.size() - 1) {
                return completedFuture(results);
            } else {
                return executeSerially(resolvers, results, i + 1);
            }
        });
    }

    private static final Object NULL = new Object();

    public CompletionStage<Map<String, Object>> executeParallelly() {
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        Set<String> awaiting = new ConcurrentHashMap<>(new HashMap<>(resolvers)).keySet();  // `keySet()` is a view and will be modified, so copy first
        Map<String, Object> results = new ConcurrentHashMap<>();
        resolvers.entrySet().forEach(entry -> {
            entry.getValue().get().thenAccept(result -> {
                String key = entry.getKey();
                Object value1 = result.getData() != null ? result.getData() : NULL;
                results.put(key, value1);
                awaiting.remove(key);
                if (awaiting.isEmpty()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    resolvers.keySet().forEach(fieldName -> {
                        Object value = results.get(fieldName);
                        map.put(fieldName, value == NULL ? null : value);
                    });
                    log.trace("completing");
                    future.complete(map);
                }
            });
        });
        return future;
    }
}
