package graphql.util;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import graphql.Internal;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.mapping;

@Internal
public class FpKit {

    //
    // From a list of named things, get a map of them by name, merging them according to the merge function
    public static <T> Map<String, T> getByName(List<T> namedObjects, Function<T, String> nameFn, BinaryOperator<T> mergeFunc) {
        return namedObjects.stream().collect(Collectors.toMap(
                nameFn,
                identity(),
                mergeFunc,
                LinkedHashMap::new)
        );
    }

    // normal groupingBy but with LinkedHashMap
    public static <T, NewKey> Map<NewKey, ImmutableList<T>> groupingBy(Collection<T> list, Function<T, NewKey> function) {
        return list.stream().collect(Collectors.groupingBy(function, LinkedHashMap::new, mapping(Function.identity(), ImmutableList.toImmutableList())));
    }

    public static <T, NewKey> Map<NewKey, T> groupingByUniqueKey(Collection<T> list, Function<T, NewKey> keyFunction) {
        return list.stream().collect(Collectors.toMap(
                keyFunction,
                identity(),
                throwingMerger(),
                LinkedHashMap::new)
        );
    }

    private static <T> BinaryOperator<T> throwingMerger() {
        return (u, v) -> {
            throw new IllegalStateException(String.format("Duplicate key %s", u));
        };
    }


    //
    // From a list of named things, get a map of them by name, merging them first one added
    public static <T> Map<String, T> getByName(List<T> namedObjects, Function<T, String> nameFn) {
        return getByName(namedObjects, nameFn, mergeFirst());
    }

    public static <T> BinaryOperator<T> mergeFirst() {
        return (o1, o2) -> o1;
    }

    /**
     * Converts an object that should be an Iterable into a Collection efficiently, leaving
     * it alone if it is already is one.  Useful when you want to get the size of something
     *
     * @param iterableResult the result object
     * @param <T>            the type of thing
     *
     * @return an Iterable from that object
     *
     * @throws java.lang.ClassCastException if its not an Iterable
     */
    @SuppressWarnings("unchecked")
    public static <T> Collection<T> toCollection(Object iterableResult) {
        if (iterableResult instanceof Collection) {
            return (Collection<T>) iterableResult;
        }
        Iterable<T> iterable = toIterable(iterableResult);
        Iterator<T> iterator = iterable.iterator();
        List<T> list = new ArrayList<>();
        while (iterator.hasNext()) {
            list.add(iterator.next());
        }
        return list;
    }

    /**
     * Converts a value into an list if its really a collection or array of things
     * else it turns it into a singleton list containing that one value
     *
     * @param possibleIterable the possible
     * @param <T>              for two
     *
     * @return an list one way or another
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> toListOrSingletonList(Object possibleIterable) {
        if (possibleIterable instanceof List) {
            return (List<T>) possibleIterable;
        }
        if (isIterable(possibleIterable)) {
            return ImmutableList.copyOf(toIterable(possibleIterable));
        }
        return ImmutableList.of((T) possibleIterable);
    }

    public static boolean isIterable(Object result) {
        return result.getClass().isArray() || result instanceof Iterable || result instanceof Stream || result instanceof Iterator;
    }


    @SuppressWarnings("unchecked")
    public static <T> Iterable<T> toIterable(Object iterableResult) {
        if (iterableResult instanceof Iterable) {
            return ((Iterable<T>) iterableResult);
        }

        if (iterableResult instanceof Stream) {
            return ((Stream<T>) iterableResult)::iterator;
        }

        if (iterableResult instanceof Iterator) {
            return () -> (Iterator<T>) iterableResult;
        }

        if (iterableResult.getClass().isArray()) {
            return () -> new ArrayIterator<>(iterableResult);
        }

        throw new ClassCastException("not Iterable: " + iterableResult.getClass());
    }

    private static class ArrayIterator<T> implements Iterator<T> {

        private final Object array;
        private final int size;
        private int i;

        private ArrayIterator(Object array) {
            this.array = array;
            this.size = Array.getLength(array);
            this.i = 0;
        }

        @Override
        public boolean hasNext() {
            return i < size;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return (T) Array.get(array, i++);
        }

    }

    public static OptionalInt toSize(Object iterableResult) {
        if (iterableResult instanceof Collection) {
            return OptionalInt.of(((Collection<?>) iterableResult).size());
        }

        if (iterableResult.getClass().isArray()) {
            return OptionalInt.of(Array.getLength(iterableResult));
        }

        return OptionalInt.empty();
    }

    /**
     * Concatenates (appends) a single elements to an existing list
     *
     * @param l   the list onto which to append the element
     * @param t   the element to append
     * @param <T> the type of elements of the list
     *
     * @return a <strong>new</strong> list composed of the first list elements and the new element
     */
    public static <T> List<T> concat(List<T> l, T t) {
        return concat(l, singletonList(t));
    }

    /**
     * Concatenates two lists into one
     *
     * @param l1  the first list to concatenate
     * @param l2  the second list to concatenate
     * @param <T> the type of element of the lists
     *
     * @return a <strong>new</strong> list composed of the two concatenated lists elements
     */
    public static <T> List<T> concat(List<T> l1, List<T> l2) {
        ArrayList<T> l = new ArrayList<>(l1);
        l.addAll(l2);
        l.trimToSize();
        return l;
    }

    //
    // quickly turn a map of values into its list equivalent
    public static <T> List<T> valuesToList(Map<?, T> map) {
        return new ArrayList<>(map.values());
    }

    public static <K, V, U> List<U> mapEntries(Map<K, V> map, BiFunction<K, V, U> function) {
        return map.entrySet().stream().map(entry -> function.apply(entry.getKey(), entry.getValue())).collect(Collectors.toList());
    }


    public static <T> List<List<T>> transposeMatrix(List<? extends List<T>> matrix) {
        int rowCount = matrix.size();
        int colCount = matrix.get(0).size();
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            for (int j = 0; j < colCount; j++) {
                T val = matrix.get(i).get(j);
                if (result.size() <= j) {
                    result.add(j, new ArrayList<>());
                }
                result.get(j).add(i, val);
            }
        }
        return result;
    }

    public static <T> CompletableFuture<List<T>> flatList(CompletableFuture<List<List<T>>> cf) {
        return cf.thenApply(FpKit::flatList);
    }

    public static <T> List<T> flatList(List<List<T>> listLists) {
        return listLists.stream()
                .flatMap(List::stream)
                .collect(ImmutableList.toImmutableList());
    }

    public static <T> Optional<T> findOne(Collection<T> list, Predicate<T> filter) {
        return list
                .stream()
                .filter(filter)
                .findFirst();
    }

    public static <T> T findOneOrNull(List<T> list, Predicate<T> filter) {
        return findOne(list, filter).orElse(null);
    }

    public static <T> int findIndex(List<T> list, Predicate<T> filter) {
        for (int i = 0; i < list.size(); i++) {
            if (filter.test(list.get(i))) {
                return i;
            }
        }
        return -1;
    }

    public static <T> List<T> filterList(Collection<T> list, Predicate<T> filter) {
        return list
                .stream()
                .filter(filter)
                .collect(Collectors.toList());
    }

    public static <T> Set<T> filterSet(Collection<T> input, Predicate<T> filter) {
        ImmutableSet.Builder<T> result = ImmutableSet.builder();
        for (T t : input) {
            if (filter.test(t)) {
                result.add(t);
            }
        }
        return result.build();
    }

    /**
     * Used in simple {@link Map#computeIfAbsent(Object, java.util.function.Function)} cases
     *
     * @param <K> for Key
     * @param <V> for Value
     *
     * @return a function that allocates a list
     */
    public static <K, V> Function<K, List<V>> newList() {
        return k -> new ArrayList<>();
    }

    /**
     * This will memoize the Supplier within the current thread's visibility, that is it does not
     * use volatile reads but rather use a sentinel check and re-reads the delegate supplier
     * value if the read has not stuck to this thread.  This means that its possible that your delegate
     * supplier MAY be called more than once across threads, but only once on the same thread.
     *
     * @param delegate the supplier to delegate to
     * @param <T>      for two
     *
     * @return a supplier that will memoize values in the context of the current thread
     */
    public static <T> Supplier<T> intraThreadMemoize(Supplier<T> delegate) {
        return new IntraThreadMemoizedSupplier<>(delegate);
    }

    /**
     * This will memoize the Supplier across threads and make sure the Supplier is exactly called once.
     * <p>
     * Use for potentially costly actions. Otherwise consider {@link #intraThreadMemoize(Supplier)}
     *
     * @param delegate the supplier to delegate to
     * @param <T>      for two
     *
     * @return a supplier that will memoize values in the context of the all the threads
     */
    public static <T> Supplier<T> interThreadMemoize(Supplier<T> delegate) {
        return new InterThreadMemoizedSupplier<>(delegate);
    }

}
