package graphql.execution.instrumentation.idempotency;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple {@link IdempotencyStore} implementation using a Map on the heap.
 *
 * @author <a href="mailto:mario@ellebrecht.com">Mario Ellebrecht &lt;mario@ellebrecht.com&gt;</a>
 */
public final class MemoryIdempotencyStore implements IdempotencyStore {

  private static final int MAP_CAPACITY_ROOT = 190;
  private static final int MAP_CAPACITY_LEAF = 16;

  private final Map<Object, Map<String, Object>> map = new ConcurrentHashMap<>(MAP_CAPACITY_ROOT);

  @Override
  public Object get(Object scope, String key) {
    return getValue(getMap(scope), key);
  }

  @Override
  public Object put(Object scope, String key, Object value) {
    return createMap(scope).put(key, value);
  }

  private Map<String, Object> createMap(Object scope) {
    return map.computeIfAbsent(scope, e -> new ConcurrentHashMap<>(MAP_CAPACITY_LEAF));
  }

  private Map<String, Object> getMap(Object scope) {
    return map.getOrDefault(scope, null);
  }

  private static Object getValue(Map<String, Object> map, String key) {
    return map == null ? null : map.get(key);
  }

}
