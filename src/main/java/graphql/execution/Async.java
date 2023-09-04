package graphql.execution;

import graphql.Assert;
import graphql.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

@Internal
@SuppressWarnings("FutureReturnValueIgnored")
public class Async {

    public interface CombinedBuilder<T> {

        void add(CompletableFuture<T> completableFuture);

        void addObject(T objectT);

        /**
         * This will return a CompletableFuture to a List<T> even if the inputs are all materialised values
         *
         * @return a CompletableFuture to a List of values
         */
        CompletableFuture<List<T>> await();

        /**
         * This will return a CompletableFuture to a List<T> if ANY of the input values are async
         * otherwise it just return a materialised List<T>
         *
         * @return either a CompletableFuture or a materialised list
         */
        /* CompletableFuture<List<T>> | List<T> */ Object awaitPolymorphic();
    }

    /**
     * Combines 0 or more CF into one. It is a wrapper around <code>CompletableFuture.allOf</code>.
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
        public void addObject(T objectT) {
            this.ix++;
        }

        @Override
        public CompletableFuture<List<T>> await() {
            Assert.assertTrue(ix == 0, () -> "expected size was " + 0 + " got " + ix);
            return typedEmpty();
        }

        @Override
        public Object awaitPolymorphic() {
            Assert.assertTrue(ix == 0, () -> "expected size was " + 0 + " got " + ix);
            return typedEmpty();
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
        public void addObject(T objectT) {
            this.value = objectT;
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
            return Collections.singletonList((T) value);
        }

        private void commonSizeAssert() {
            Assert.assertTrue(ix == 1, () -> "expected size was " + 1 + " got " + ix);
        }
    }

    private static class Many<T> implements CombinedBuilder<T> {

        private final Object[] array;
        private int ix;
        private boolean containsCFs;

        @SuppressWarnings("unchecked")
        private Many(int size) {
            this.array = new Object[size];
            this.ix = 0;
            containsCFs = false;
        }

        @Override
        public void add(CompletableFuture<T> completableFuture) {
            array[ix++] = completableFuture;
            containsCFs = true;
        }

        @Override
        public void addObject(T objectT) {
            array[ix++] = objectT;
        }

        @SuppressWarnings("unchecked")
        @Override
        public CompletableFuture<List<T>> await() {
            commonSizeAssert();

            CompletableFuture<List<T>> overallResult = new CompletableFuture<>();
            if (!containsCFs) {
                List<T> results = new ArrayList<>(array.length);
                for (Object object : array) {
                    results.add((T) object);
                }
                overallResult.complete(results);
            } else {
                CompletableFuture<?>[] cfsArr = copyOnlyCFsToArray();
                CompletableFuture.allOf(cfsArr)
                        .whenComplete((ignored, exception) -> {
                            if (exception != null) {
                                overallResult.completeExceptionally(exception);
                                return;
                            }
                            List<T> results = new ArrayList<>(array.length);
                            for (Object object : array) {
                                if (object instanceof CompletableFuture) {
                                    CompletableFuture<T> cf = (CompletableFuture<T>) object;
                                    results.add(cf.join());
                                } else {
                                    results.add((T) object);
                                }
                            }
                            overallResult.complete(results);
                        });
            }
            return overallResult;
        }

        @Override
        public Object awaitPolymorphic() {
            if (!containsCFs) {
                commonSizeAssert();
                List<T> results = new ArrayList<>(array.length);
                for (Object object : array) {
                    results.add((T) object);
                }
                return results;
            } else {
                return await();
            }
        }

        private void commonSizeAssert() {
            Assert.assertTrue(ix == array.length, () -> "expected size was " + array.length + " got " + ix);
        }

        @NotNull
        private CompletableFuture<?>[] copyOnlyCFsToArray() {
            return Arrays.stream(array)
                    .filter(obj -> obj instanceof CompletableFuture)
                    .toArray(CompletableFuture[]::new);
        }

    }

    public static <T, U> CompletableFuture<List<U>> each(Collection<T> list, Function<T, CompletableFuture<U>> cfFactory) {
        CombinedBuilder<U> futures = ofExpectedSize(list.size());
        for (T t : list) {
            CompletableFuture<U> cf;
            try {
                cf = cfFactory.apply(t);
                Assert.assertNotNull(cf, () -> "cfFactory must return a non null value");
            } catch (Exception e) {
                cf = new CompletableFuture<>();
                // Async.each makes sure that it is not a CompletionException inside a CompletionException
                cf.completeExceptionally(new CompletionException(e));
            }
            futures.add(cf);
        }
        return futures.await();
    }

    public static <T, U> CompletableFuture<List<U>> eachSequentially(Iterable<T> list, BiFunction<T, List<U>, CompletableFuture<U>> cfFactory) {
        CompletableFuture<List<U>> result = new CompletableFuture<>();
        eachSequentiallyImpl(list.iterator(), cfFactory, new ArrayList<>(), result);
        return result;
    }

    private static <T, U> void eachSequentiallyImpl(Iterator<T> iterator, BiFunction<T, List<U>, CompletableFuture<U>> cfFactory, List<U> tmpResult, CompletableFuture<List<U>> overallResult) {
        if (!iterator.hasNext()) {
            overallResult.complete(tmpResult);
            return;
        }
        CompletableFuture<U> cf;
        try {
            cf = cfFactory.apply(iterator.next(), tmpResult);
            Assert.assertNotNull(cf, () -> "cfFactory must return a non null value");
        } catch (Exception e) {
            cf = new CompletableFuture<>();
            cf.completeExceptionally(new CompletionException(e));
        }
        cf.whenComplete((cfResult, exception) -> {
            if (exception != null) {
                overallResult.completeExceptionally(exception);
                return;
            }
            tmpResult.add(cfResult);
            eachSequentiallyImpl(iterator, cfFactory, tmpResult, overallResult);
        });
    }


    /**
     * Turns an object T into a CompletableFuture if it's not already
     *
     * @param t   - the object to check
     * @param <T> for two
     *
     * @return a CompletableFuture
     */
    public static <T> CompletableFuture<T> toCompletableFuture(T t) {
        if (t instanceof CompletionStage) {
            //noinspection unchecked
            return ((CompletionStage<T>) t).toCompletableFuture();
        } else {
            return CompletableFuture.completedFuture(t);
        }
    }

    public static <T> CompletableFuture<T> asCompletableFuture(Object t) {
        if (t instanceof CompletionStage) {
            //noinspection unchecked
            return ((CompletionStage<T>) t).toCompletableFuture();
        } else {
            //noinspection unchecked
            return (CompletableFuture<T>) CompletableFuture.completedFuture(t);
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
     * If the passed in CompletableFuture is null then it creates a CompletableFuture that resolves to null
     *
     * @param completableFuture the CF to use
     * @param <T>               for two
     *
     * @return the completableFuture if it's not null or one that always resoles to null
     */
    public static <T> @NotNull CompletableFuture<T> orNullCompletedFuture(@Nullable CompletableFuture<T> completableFuture) {
        return completableFuture != null ? completableFuture : CompletableFuture.completedFuture(null);
    }
}
