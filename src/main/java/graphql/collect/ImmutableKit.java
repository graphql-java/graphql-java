package graphql.collect;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import graphql.Internal;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static graphql.Assert.assertNotNull;

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
     * ImmutableMaps are hard to build via {@link Map#computeIfAbsent(Object, Function)} style.  This method
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
        assertNotNull(startingMap);
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
     * @param iterable the iterable to map
     * @param mapper   the mapper function
     * @param <T>      for two
     * @param <R>      for result
     *
     * @return a map immutable list of results
     */
    public static <T, R> ImmutableList<R> map(Iterable<? extends T> iterable, Function<? super T, ? extends R> mapper) {
        assertNotNull(iterable);
        assertNotNull(mapper);
        @SuppressWarnings("RedundantTypeArguments")
        ImmutableList.Builder<R> builder = ImmutableList.<R>builder();
        for (T t : iterable) {
            R r = mapper.apply(t);
            builder.add(r);
        }
        return builder.build();
    }

    /**
     * This constructs a new Immutable list from an existing collection and adds a new element to it.
     *
     * @param existing    the existing collection
     * @param newValue    the new value to add
     * @param extraValues more values to add
     * @param <T>         for two
     *
     * @return an Immutable list with the extra items.
     */
    public static <T> ImmutableList<T> addToList(Collection<? extends T> existing, T newValue, T... extraValues) {
        assertNotNull(existing);
        assertNotNull(newValue);
        int expectedSize = existing.size() + 1 + extraValues.length;
        ImmutableList.Builder<T> newList = ImmutableList.builderWithExpectedSize(expectedSize);
        newList.addAll(existing);
        newList.add(newValue);
        for (T extraValue : extraValues) {
            newList.add(extraValue);
        }
        return newList.build();
    }

    /**
     * This constructs a new Immutable set from an existing collection and adds a new element to it.
     *
     * @param existing    the existing collection
     * @param newValue    the new value to add
     * @param extraValues more values to add
     * @param <T>         for two
     *
     * @return an Immutable Set with the extra items.
     */
    public static <T> ImmutableSet<T> addToSet(Collection<? extends T> existing, T newValue, T... extraValues) {
        assertNotNull(existing);
        assertNotNull(newValue);
        int expectedSize = existing.size() + 1 + extraValues.length;
        ImmutableSet.Builder<T> newSet = ImmutableSet.builderWithExpectedSize(expectedSize);
        newSet.addAll(existing);
        newSet.add(newValue);
        for (T extraValue : extraValues) {
            newSet.add(extraValue);
        }
        return newSet.build();
    }

}
