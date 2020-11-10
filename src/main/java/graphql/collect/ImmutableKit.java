package graphql.collect;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.Assert;
import graphql.Internal;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Internal
public final class ImmutableKit {

    public static <T> ImmutableList<T> emptyList() {
        return ImmutableList.of();
    }

    public static <T> ImmutableList<T> nonNullCopyOf(Collection<T> collection) {
        return collection == null ? emptyList() : ImmutableList.copyOf(collection);
    }

    public static <K, V> ImmutableMap<K, V> emptyMap() {
        return ImmutableMap.of();
    }

    /**
     * ImmutableMaps are hard to build via {@link Map#computeIfAbsent(Object, Function)} style.  This methods
     * allows you to take a mutable map with mutable list of keys and make it immutable.
     * <p>
     * This of course has a cost - if the map is very large you will be using more memory.  But for static
     * maps that live a long life it maybe be worth it.
     *
     * @param startingMap the starting input map
     * @param <K>         for key
     * @param <V>         for victory
     *
     * @return and Immutable map of ImmutableList values
     */

    public static <K, V> ImmutableMap<K, ImmutableList<V>> toImmutableMapOfLists(Map<K, List<V>> startingMap) {
        Assert.assertNotNull(startingMap);
        ImmutableMap.Builder<K, ImmutableList<V>> map = ImmutableMap.builder();
        for (Map.Entry<K, List<V>> e : startingMap.entrySet()) {
            ImmutableList<V> value = ImmutableList.copyOf(startingMap.getOrDefault(e.getKey(), emptyList()));
            map.put(e.getKey(), value);
        }
        return map.build();
    }


    public static <K, V> ImmutableMap<K, V> addToMap(Map<K, V> existing, K newKey, V newVal) {
        return ImmutableMap.<K, V>builder().putAll(existing).put(newKey, newVal).build();
    }

    public static <K, V> ImmutableMap<K, V> mergeMaps(Map<K, V> m1, Map<K, V> m2) {
        return ImmutableMap.<K, V>builder().putAll(m1).putAll(m2).build();
    }

    public static <T> ImmutableList<T> concatLists(List<T> l1, List<T> l2) {
        return ImmutableList.<T>builder().addAll(l1).addAll(l2).build();
    }

    /**
     * This is more efficient than `c.stream().map().collect()` because it does not create the intermediate objects needed
     * for the flexible style.  Benchmarking has shown this to outperform `stream()`.
     *
     * @param collection the collection to map
     * @param mapper     the mapper function
     * @param <T>        for two
     * @param <R>        for result
     *
     * @return a map immutable list of results
     */
    public static <T, R> ImmutableList<R> map(Collection<T> collection, Function<? super T, ? extends R> mapper) {
        Assert.assertNotNull(collection);
        Assert.assertNotNull(mapper);
        @SuppressWarnings("RedundantTypeArguments")
        ImmutableList.Builder<R> builder = ImmutableList.<R>builder();
        for (T t : collection) {
            R r = mapper.apply(t);
            builder.add(r);
        }
        return builder.build();
    }
}
