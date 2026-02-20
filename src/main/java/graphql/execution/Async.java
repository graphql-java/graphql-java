package graphql.execution;

import graphql.Assert;
import graphql.Internal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static graphql.Assert.assertTrue;
import static java.util.stream.Collectors.toList;

@Internal
@SuppressWarnings("FutureReturnValueIgnored")
public class Async {

    /**
     * A builder of materialized objects or {@link CompletableFuture}s than can present a promise to the list of them
     * <p>
     * This builder has a strict contract on size whereby if the expectedSize is five, then there MUST be five elements presented to it.
     *
     * @param <T> for two
     */
    public interface CombinedBuilder<T> {

        /**
         * This adds a {@link CompletableFuture} into the collection of results
         *
         * @param completableFuture the CF to add
         */
        void add(CompletableFuture<T> completableFuture);

        /**
         * This adds a new value which can be either a materialized value or a {@link CompletableFuture}
         *
         * @param object the object to add
         */
        void addObject(Object object);

        /**
         * This will return a {@code CompletableFuture<List<T>>} even if the inputs are all materialized values
         *
         * @return a CompletableFuture to a List of values
         */
        CompletableFuture<List<T>> await();

        /**
         * This will return a {@code CompletableFuture<List<T>>} if ANY of the input values are async
         * otherwise it just return a materialised {@code List<T>}
         *
         * @return either a CompletableFuture or a materialized list
         */
        /* CompletableFuture<List<T>> | List<T> */ Object awaitPolymorphic();
    }

    /**
     * Combines zero or more CFs into one. It is a wrapper around <code>CompletableFuture.allOf</code>.
     *
     * @param expectedSize how many we expect
     * @param <T>          for two
     *
     * @return a combined builder of CFs
     */
    public static <T> CombinedBuilder<T> ofExpectedSize(int expectedSize) {
        if (expectedSize == 0) {
            return new Empty<>();
        } else if (expectedSize == 1) {
            return new Single<>();
        } else {
            return new Many<>(expectedSize);
        }
    }

    private static class Empty<T> implements CombinedBuilder<T> {

        private int ix;

        @Override
        public void add(CompletableFuture<T> completableFuture) {
            this.ix++;
        }

        @Override
        public void addObject(Object object) {
            this.ix++;
        }

        @Override
        public CompletableFuture<List<T>> await() {
            assertTrue(ix == 0, "expected size was 0 got %d", ix);
            return typedEmpty();
        }

        @Override
        public Object awaitPolymorphic() {
            Assert.assertTrue(ix == 0, () -> "expected size was " + 0 + " got " + ix);
            return Collections.emptyList();
        }

        // implementation details: infer the type of Completable<List<T>> from a singleton empty
        private static final CompletableFuture<List<?>> EMPTY = CompletableFuture.completedFuture(Collections.emptyList());

        @SuppressWarnings("unchecked")
        private static <T> CompletableFuture<T> typedEmpty() {
            return (CompletableFuture<T>) EMPTY;
        }
    }

    private static class Single<T> implements CombinedBuilder<T> {

        // avoiding array allocation as there is only 1 CF
        private Object value;
        private int ix;

        @Override
        public void add(CompletableFuture<T> completableFuture) {
            this.value = completableFuture;
            this.ix++;
        }

        @Override
        public void addObject(Object object) {
            this.value = object;
            this.ix++;
        }

        @Override
        public CompletableFuture<List<T>> await() {
            commonSizeAssert();
            if (value instanceof CompletableFuture) {
                @SuppressWarnings("unchecked")
                CompletableFuture<T> cf = (CompletableFuture<T>) value;
                return cf.thenApply(Collections::singletonList);
            }
            //noinspection unchecked
            return CompletableFuture.completedFuture(Collections.singletonList((T) value));
        }

        @Override
        public Object awaitPolymorphic() {
            commonSizeAssert();
            if (value instanceof CompletableFuture) {
                @SuppressWarnings("unchecked")
                CompletableFuture<T> cf = (CompletableFuture<T>) value;
                return cf.thenApply(Collections::singletonList);
            }
            //noinspection unchecked
            return Collections.singletonList((T) value);
        }

        private void commonSizeAssert() {
            Assert.assertTrue(ix == 1, () -> "expected size was " + 1 + " got " + ix);
        }
    }

    private static class Many<T> implements CombinedBuilder<T> {

        private final Object[] array;
        private int ix;
        private int cfCount;

        private Many(int size) {
            this.array = new Object[size];
            this.ix = 0;
            cfCount = 0;
        }

        @Override
        public void add(CompletableFuture<T> completableFuture) {
            array[ix++] = completableFuture;
            cfCount++;
        }

        @Override
        public void addObject(Object object) {
            array[ix++] = object;
            if (object instanceof CompletableFuture) {
                cfCount++;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public CompletableFuture<List<T>> await() {
            commonSizeAssert();

            CompletableFuture<List<T>> overallResult = new CompletableFuture<>();
            if (cfCount == 0) {
                overallResult.complete(materialisedList(array));
            } else {
                CompletableFuture<T>[] cfsArr = copyOnlyCFsToArray();
                CompletableFuture.allOf(cfsArr)
                        .whenComplete((ignored, exception) -> {
                            if (exception != null) {
                                overallResult.completeExceptionally(exception);
                                return;
                            }
                            List<T> results = new ArrayList<>(array.length);
                            if (cfsArr.length == array.length) {
                                // they are all CFs
                                for (CompletableFuture<T> cf : cfsArr) {
                                    results.add(cf.join());
                                }
                            } else {
                                // it's a mixed bag of CFs and materialized objects
                                for (Object object : array) {
                                    if (object instanceof CompletableFuture) {
                                        CompletableFuture<T> cf = (CompletableFuture<T>) object;
                                        // join is safe since they are all completed earlier via CompletableFuture.allOf()
                                        results.add(cf.join());
                                    } else {
                                        results.add((T) object);
                                    }
                                }
                            }
                            overallResult.complete(results);
                        });
            }
            return overallResult;
        }

        @SuppressWarnings("unchecked")
        @NonNull
        private CompletableFuture<T>[] copyOnlyCFsToArray() {
            if (cfCount == array.length) {
                // if it's all CFs - make a type safe copy via C code
                return Arrays.copyOf(array, array.length, CompletableFuture[].class);
            } else {
                int i = 0;
                CompletableFuture<T>[] dest = new CompletableFuture[cfCount];
                for (Object o : array) {
                    if (o instanceof CompletableFuture) {
                        dest[i] = (CompletableFuture<T>) o;
                        i++;
                    }
                }
                return dest;
            }
        }

        @Override
        public Object awaitPolymorphic() {
            if (cfCount == 0) {
                commonSizeAssert();
                return materialisedList(array);
            } else {
                return await();
            }
        }

        @NonNull
        @SuppressWarnings("unchecked")
        private List<T> materialisedList(Object[] array) {
            return (List<T>) Arrays.asList(array);
        }

        private void commonSizeAssert() {
            Assert.assertTrue(ix == array.length, () -> "expected size was " + array.length + " got " + ix);
        }

    }

    @SuppressWarnings("unchecked")
    public static <T, U> CompletableFuture<List<U>> each(Collection<T> list, Function<T, Object> cfOrMaterialisedValueFactory) {
        Object l = eachPolymorphic(list, cfOrMaterialisedValueFactory);
        if (l instanceof CompletableFuture) {
            return (CompletableFuture<List<U>>) l;
        } else {
            return CompletableFuture.completedFuture((List<U>) l);
        }
    }

    /**
     * This will run the value factory for each of the values in the provided list.
     * <p>
     * If any of the values provided is a {@link CompletableFuture} it will return a {@link CompletableFuture} result object
     * that joins on all values otherwise if none of the values are a {@link CompletableFuture} then it will return a materialized list.
     *
     * @param list                         the list to work over
     * @param cfOrMaterialisedValueFactory the value factory to call for each iterm in the list
     * @param <T>                          for two
     *
     * @return a {@link CompletableFuture} to the list of resolved values or the list of values in a materialized fashion
     */
    public static <T> /* CompletableFuture<List<U>> | List<U> */ Object eachPolymorphic(Collection<T> list, Function<T, Object> cfOrMaterialisedValueFactory) {
        CombinedBuilder<Object> futures = ofExpectedSize(list.size());
        for (T t : list) {
            try {
                Object value = cfOrMaterialisedValueFactory.apply(t);
                futures.addObject(value);
            } catch (Exception e) {
                CompletableFuture<Object> cf = new CompletableFuture<>();
                // Async.each makes sure that it is not a CompletionException inside a CompletionException
                cf.completeExceptionally(new CompletionException(e));
                futures.add(cf);
            }
        }
        return futures.awaitPolymorphic();
    }

    public static <T, U> CompletableFuture<List<U>> eachSequentially(Iterable<T> list, BiFunction<T, List<U>, Object> cfOrMaterialisedValueFactory) {
        CompletableFuture<List<U>> result = new CompletableFuture<>();
        eachSequentiallyPolymorphicImpl(list.iterator(), cfOrMaterialisedValueFactory, new ArrayList<>(), result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T, U> void eachSequentiallyPolymorphicImpl(Iterator<T> iterator, BiFunction<T, List<U>, Object> cfOrMaterialisedValueFactory, List<U> tmpResult, CompletableFuture<List<U>> overallResult) {
        if (!iterator.hasNext()) {
            overallResult.complete(tmpResult);
            return;
        }
        Object value;
        try {
            value = cfOrMaterialisedValueFactory.apply(iterator.next(), tmpResult);
        } catch (Exception e) {
            overallResult.completeExceptionally(new CompletionException(e));
            return;
        }
        if (value instanceof CompletableFuture) {
            CompletableFuture<U> cf = (CompletableFuture<U>) value;
            cf.whenComplete((cfResult, exception) -> {
                if (exception != null) {
                    overallResult.completeExceptionally(exception);
                    return;
                }
                tmpResult.add(cfResult);
                eachSequentiallyPolymorphicImpl(iterator, cfOrMaterialisedValueFactory, tmpResult, overallResult);
            });
        } else {
            tmpResult.add((U) value);
            eachSequentiallyPolymorphicImpl(iterator, cfOrMaterialisedValueFactory, tmpResult, overallResult);
        }
    }


    /**
     * Turns an object T into a CompletableFuture if it's not already
     *
     * @param t   - the object to check
     * @param <T> for two
     *
     * @return a CompletableFuture
     */
    @SuppressWarnings("unchecked")
    public static <T> CompletableFuture<T> toCompletableFuture(Object t) {
        if (t instanceof CompletionStage) {
            return ((CompletionStage<T>) t).toCompletableFuture();
        } else {
            return CompletableFuture.completedFuture((T) t);
        }
    }

    /**
     * Turns a CompletionStage into a CompletableFuture if it's not already, otherwise leaves it alone
     * as a materialized object.
     *
     * @param object - the object to check
     *
     * @return a CompletableFuture from a CompletionStage or the materialized object itself
     */
    public static Object toCompletableFutureOrMaterializedObject(Object object) {
        if (object instanceof CompletionStage) {
            return ((CompletionStage<?>) object).toCompletableFuture();
        } else {
            return object;
        }
    }

    public static <T> CompletableFuture<T> tryCatch(Supplier<CompletableFuture<T>> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            CompletableFuture<T> result = new CompletableFuture<>();
            result.completeExceptionally(e);
            return result;
        }
    }

    public static <T> CompletableFuture<T> exceptionallyCompletedFuture(Throwable exception) {
        CompletableFuture<T> result = new CompletableFuture<>();
        result.completeExceptionally(exception);
        return result;
    }

    /**
     * If the passed in CompletableFuture is null, then it creates a CompletableFuture that resolves to null
     *
     * @param completableFuture the CF to use
     * @param <T>               for two
     *
     * @return the completableFuture if it's not null or one that always resoles to null
     */
    public static <T> @NonNull CompletableFuture<T> orNullCompletedFuture(@Nullable CompletableFuture<T> completableFuture) {
        return completableFuture != null ? completableFuture : CompletableFuture.completedFuture(null);
    }

    public static <T> CompletableFuture<List<T>> allOf(List<CompletableFuture<T>> cfs) {
        return CompletableFuture.allOf(cfs.toArray(CompletableFuture[]::new))
                .thenApply(v -> cfs.stream()
                        .map(CompletableFuture::join)
                        .collect(toList())
                );
    }

    public static <K, V> CompletableFuture<Map<K, V>> allOf(Map<K, CompletableFuture<V>> cfs) {
        return CompletableFuture.allOf(cfs.values().toArray(CompletableFuture[]::new))
                .thenApply(v -> cfs.entrySet().stream()
                        .collect(
                                Collectors.toMap(
                                        Map.Entry::getKey,
                                        task -> task.getValue().join())
                        )
                );
    }

}
