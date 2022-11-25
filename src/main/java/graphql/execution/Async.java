package graphql.execution;

import graphql.Assert;
import graphql.Internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@Internal
@SuppressWarnings("FutureReturnValueIgnored")
public class Async {

    public interface CombinedBuilder<T> {

        void add(CompletableFuture<T> completableFuture);

        CompletableFuture<List<T>> await();
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
        public CompletableFuture<List<T>> await() {
            Assert.assertTrue(ix == 0, () -> "expected size was " + 0 + " got " + ix);
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
    }

    private static class Single<T> implements CombinedBuilder<T> {

        // avoiding array allocation as there is only 1 CF
        private CompletableFuture<T> completableFuture;
        private int ix;

        @Override
        public void add(CompletableFuture<T> completableFuture) {
            this.completableFuture = completableFuture;
            this.ix++;
        }

        @Override
        public CompletableFuture<List<T>> await() {
            Assert.assertTrue(ix == 1, () -> "expected size was " + 1 + " got " + ix);

            CompletableFuture<List<T>> overallResult = new CompletableFuture<>();
            completableFuture
                    .whenComplete((ignored, exception) -> {
                        if (exception != null) {
                            overallResult.completeExceptionally(exception);
                            return;
                        }
                        List<T> results = Collections.singletonList(completableFuture.join());
                        overallResult.complete(results);
                    });
            return overallResult;
        }
    }

    private static class Many<T> implements CombinedBuilder<T> {

        private final CompletableFuture<T>[] array;
        private int ix;

        @SuppressWarnings("unchecked")
        private Many(int size) {
            this.array = new CompletableFuture[size];
            this.ix = 0;
        }

        @Override
        public void add(CompletableFuture<T> completableFuture) {
            array[ix++] = completableFuture;
        }

        @Override
        public CompletableFuture<List<T>> await() {
            Assert.assertTrue(ix == array.length, () -> "expected size was " + array.length + " got " + ix);

            CompletableFuture<List<T>> overallResult = new CompletableFuture<>();
            CompletableFuture.allOf(array)
                    .whenComplete((ignored, exception) -> {
                        if (exception != null) {
                            overallResult.completeExceptionally(exception);
                            return;
                        }
                        List<T> results = new ArrayList<>(array.length);
                        for (CompletableFuture<T> future : array) {
                            results.add(future.join());
                        }
                        overallResult.complete(results);
                    });
            return overallResult;
        }

    }

    @FunctionalInterface
    public interface CFFactory<T, U> {
        CompletableFuture<U> apply(T input, int index, List<U> previousResults);
    }

    public static <T, U> CompletableFuture<List<U>> each(Collection<T> list, BiFunction<T, Integer, CompletableFuture<U>> cfFactory) {
        CombinedBuilder<U> futures = ofExpectedSize(list.size());
        int index = 0;
        for (T t : list) {
            CompletableFuture<U> cf;
            try {
                cf = cfFactory.apply(t, index++);
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

    public static <T, U> CompletableFuture<List<U>> eachSequentially(Iterable<T> list, CFFactory<T, U> cfFactory) {
        CompletableFuture<List<U>> result = new CompletableFuture<>();
        eachSequentiallyImpl(list.iterator(), cfFactory, 0, new ArrayList<>(), result);
        return result;
    }

    private static <T, U> void eachSequentiallyImpl(Iterator<T> iterator, CFFactory<T, U> cfFactory, int index, List<U> tmpResult, CompletableFuture<List<U>> overallResult) {
        if (!iterator.hasNext()) {
            overallResult.complete(tmpResult);
            return;
        }
        CompletableFuture<U> cf;
        try {
            cf = cfFactory.apply(iterator.next(), index, tmpResult);
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
            eachSequentiallyImpl(iterator, cfFactory, index + 1, tmpResult, overallResult);
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

}
