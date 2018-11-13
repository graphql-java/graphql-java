package graphql;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import static graphql.Assert.assertNotNull;

@SuppressWarnings("unchecked")
@PublicApi
public class GraphqlContext {

    private final ConcurrentMap<Object, Object> map;

    private GraphqlContext(ConcurrentMap<Object, Object> map) {
        this.map = map;
    }

    public <T> T get(Object key) {
        return (T) map.get(assertNotNull(key));
    }

    public <T> T getOrDefault(Object key, T defaultValue) {
        return (T) map.getOrDefault(assertNotNull(key), defaultValue);
    }

    public <T> Optional<T> getOrEmpty(Object key) {
        T t = (T) map.get(assertNotNull(key));
        return Optional.ofNullable(t);
    }

    public boolean hasKey(Object key) {
        return map.containsKey(key);
    }

    public void put(Object key, Object value) {
        map.put(assertNotNull(key), value);
    }

    public void putAll(GraphqlContext context) {
        assertNotNull(context);
        map.putAll(context.map);
    }

    public Stream<Map.Entry<Object, Object>> stream() {
        return map.entrySet().stream();
    }

    public static Builder newContext() {
        return new Builder();
    }

    public static class Builder {
        private final ConcurrentMap<Object, Object> map = new ConcurrentHashMap<>();

        public GraphqlContext build() {
            return new GraphqlContext(map);
        }
    }


}
