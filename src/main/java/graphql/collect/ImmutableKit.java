package graphql.collect;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import graphql.Internal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import static graphql.Assert.assertNotNull;

@Internal
@NullMarked
public final class ImmutableKit {

    public static <T> ImmutableList<T> emptyList() {
        return ImmutableList.of();
    }

    public static <T> ImmutableList<T> nonNullCopyOf(@Nullable Collection<T> collection) {
        return collection == null ? emptyList() : ImmutableList.copyOf(collection);
    }

    public static <K, V> ImmutableMap<K, V> emptyMap() {
        return ImmutableMap.of();
    }

    public static <K, V> ImmutableMap<K, V> addToMap(Map<K, V> existing, K newKey, V newVal) {
        return ImmutableMap.<K, V>builder().putAll(existing).put(newKey, newVal).build();
    }

    public static <T> ImmutableList<T> concatLists(List<T> l1, List<T> l2) {
        return ImmutableList.<T>builderWithExpectedSize(l1.size() + l2.size()).addAll(l1).addAll(l2).build();
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
    public static <T, R> ImmutableList<R> map(Collection<? extends T> collection, Function<? super T, ? extends R> mapper) {
        assertNotNull(collection);
        assertNotNull(mapper);
        ImmutableList.Builder<R> builder = ImmutableList.builderWithExpectedSize(collection.size());
        for (T t : collection) {
            R r = mapper.apply(t);
            builder.add(r);
        }
        return builder.build();
    }

    /**
     * This is more efficient than `c.stream().filter().collect()` because it does not create the intermediate objects needed
     * for the flexible style.  Benchmarking has shown this to outperform `stream()`.
     *
     * @param collection the collection to map
     * @param filter     the filter predicate
     * @param <T>        for two
     *
     * @return a map immutable list of results
     */
    public static <T> ImmutableList<T> filter(Collection<? extends T> collection, Predicate<? super T> filter) {
        assertNotNull(collection);
        assertNotNull(filter);
        return filterAndMap(collection, filter, Function.identity());
    }

    /**
     * This is more efficient than `c.stream().filter().map().collect()` because it does not create the intermediate objects needed
     * for the flexible style.  Benchmarking has shown this to outperform `stream()`.
     *
     * @param collection the collection to map
     * @param filter     the filter predicate
     * @param mapper     the mapper function
     * @param <T>        for two
     * @param <R>        for result
     *
     * @return a map immutable list of results
     */
    public static <T, R> ImmutableList<R> filterAndMap(Collection<? extends T> collection, Predicate<? super T> filter, Function<? super T, ? extends R> mapper) {
        assertNotNull(collection);
        assertNotNull(mapper);
        assertNotNull(filter);
        ImmutableList.Builder<R> builder = ImmutableList.builderWithExpectedSize(collection.size());
        for (T t : collection) {
            if (filter.test(t)) {
                R r = mapper.apply(t);
                builder.add(r);
            }
        }
        return builder.build();
    }

    public static <T> ImmutableList<T> flatMapList(Collection<List<T>> listLists) {
        ImmutableList.Builder<T> builder = ImmutableList.builder();
        for (List<T> t : listLists) {
            builder.addAll(t);
        }
        return builder.build();
    }


    /**
     * This will map a collection of items but drop any that are null from the input.
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
    public static <T, R> ImmutableList<R> mapAndDropNulls(Collection<? extends T> collection, Function<? super T, ? extends R> mapper) {
        assertNotNull(collection);
        assertNotNull(mapper);
        ImmutableList.Builder<R> builder = ImmutableList.builderWithExpectedSize(collection.size());
        for (T t : collection) {
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
        ImmutableSet.Builder<T> newSet = ImmutableSet.builderWithExpectedSize(expectedSize);
        newSet.addAll(existing);
        newSet.add(newValue);
        for (T extraValue : extraValues) {
            newSet.add(extraValue);
        }
        return newSet.build();
    }

}
