package graphql.util.flyweight;

import graphql.Internal;
import graphql.util.LockKit;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

@Internal
@NullMarked
public class FlyweightKit {

    public static class BiKeyMap<K1, K2, T> {
        private final Map<K1, Map<K2, T>> map;
        private final LockKit.ReentrantLock lock;


        public BiKeyMap() {
            map = new HashMap<>();
            lock = new LockKit.ReentrantLock();
        }

        public T computeIfAbsent(K1 key1, K2 key2, BiFunction<K1, K2, T> computeFunc) {
            lock.lock();
            try {
                Map<K2, T> k2Map = map.computeIfAbsent(key1, k1 -> new HashMap<>());
                return k2Map.computeIfAbsent(key2, k2 -> computeFunc.apply(key1, k2));
            } finally {
                lock.unlock();
            }
        }

        public void clear() {
            clearMap(map, lock);
        }
    }

    public interface TriFunction<K1, K2, K3, T> {
        T apply(K1 k1, K2 k2, K3 k3);
    }

    public static class TriKeyMap<K1, K2, K3, T> {
        private final Map<K1, Map<K2, Map<K3, T>>> map;
        private final LockKit.ReentrantLock lock;

        public TriKeyMap() {
            map = new HashMap<>();
            lock = new LockKit.ReentrantLock();
        }

        public T computeIfAbsent(K1 key1, K2 key2, K3 key3, TriFunction<K1, K2, K3, T> computeFunc) {
            lock.lock();
            try {
                Map<K2, Map<K3, T>> k2Map = map.computeIfAbsent(key1, k1 -> new HashMap<>());
                Map<K3, T> k3Map = k2Map.computeIfAbsent(key2, k2 -> new HashMap<>());
                return k3Map.computeIfAbsent(key3, k3 -> computeFunc.apply(key1, key2, k3));
            } finally {
                lock.unlock();
            }
        }

        public void clear() {
            clearMap(map, lock);
        }
    }

    @SuppressWarnings("rawtypes")
    private static void clearMap(Map map, LockKit.ReentrantLock lock) {
        lock.lock();
        try {
            map.clear();
        } finally {
            lock.unlock();
        }
    }

}
