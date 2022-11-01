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

    public static <K, V> ImmutableMap<K, V> addToMap(Map<K, V> existing, K newKey, V newVal) {
        return ImmutableMap.<K, V>builder().putAll(existing).put(newKey, newVal).build();
    }

    public static <T> ImmutableList<T> concatLists(List<T> l1, List<T> l2) {
        //noinspection UnstableApiUsage
        return ImmutableList.<T>builderWithExpectedSize(l1.size() + l2.size()).addAll(l1).addAll(l2).build();
    }

    /**
     * This is more efficient than `c.stream().map().collect()` because it does not create the intermediate objects needed
     * for the flexible style.  Benchmarking has shown this to outperform `stream()`.
     *
     * @param collection the collection to map
     * @param mapper   the mapper function
     * @param <T>      for two
     * @param <R>      for result
     *
     * @return a map immutable list of results
     */
    public static <T, R> ImmutableList<R> map(Collection<? extends T> collection, Function<? super T, ? extends R> mapper) {
        assertNotNull(collection);
        assertNotNull(mapper);
        @SuppressWarnings({"RedundantTypeArguments", "UnstableApiUsage"})
        ImmutableList.Builder<R> builder = ImmutableList.<R>builderWithExpectedSize(collection.size());
        for (T t : collection) {
            R r = mapper.apply(t);
            builder.add(r);
        }
        return builder.build();
    }

    /**
     * This will map an iterable of items but drop any that are null from the mapped list
     *
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
    public static <T, R> ImmutableList<R> mapAndDropNulls(Iterable<? extends T> iterable, Function<? super T, ? extends R> mapper) {
        ImmutableList.Builder<R> builder = ImmutableList.builder();
        for (T t : iterable) {
            R r = mapper.apply(t);
            if (r != null) {
                builder.add(r);
            }
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
    @SafeVarargs
    public static <T> ImmutableList<T> addToList(Collection<? extends T> existing, T newValue, T... extraValues) {
        assertNotNull(existing);
        assertNotNull(newValue);
        int expectedSize = existing.size() + 1 + extraValues.length;
        @SuppressWarnings("UnstableApiUsage")
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
    @SafeVarargs
    public static <T> ImmutableSet<T> addToSet(Collection<? extends T> existing, T newValue, T... extraValues) {
        assertNotNull(existing);
        assertNotNull(newValue);
        int expectedSize = existing.size() + 1 + extraValues.length;
        @SuppressWarnings("UnstableApiUsage")
        ImmutableSet.Builder<T> newSet = ImmutableSet.builderWithExpectedSize(expectedSize);
        newSet.addAll(existing);
        newSet.add(newValue);
        for (T extraValue : extraValues) {
            newSet.add(extraValue);
        }
        return newSet.build();
    }

}
