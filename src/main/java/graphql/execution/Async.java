package graphql.execution;

import graphql.Assert;
import graphql.Internal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;

@Internal
public class Async {

    @FunctionalInterface
    public interface CFFactory<T, U> {
        CompletableFuture<U> apply(T input, int index, List<U> previousResults);
    }

    public static <U> CompletableFuture<List<U>> each(List<CompletableFuture<U>> futures) {
        CompletableFuture<List<U>> overallResult = new CompletableFuture<>();
        CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[futures.size()]))
                .whenComplete((noUsed, exception) -> {
                    if (exception != null) {
                        overallResult.completeExceptionally(exception);
                        return;
                    }
                    List<U> results = new ArrayList<>();
                    for (CompletableFuture<U> future : futures) {
                        results.add(future.join());
                    }
                    overallResult.complete(results);
                });
        return overallResult;
    }

    public static <T, U> CompletableFuture<List<U>> each(Iterable<T> list, BiFunction<T, Integer, CompletableFuture<U>> cfFactory) {
        List<CompletableFuture<U>> futures = new ArrayList<>();
        int index = 0;
        for (T t : list) {
            CompletableFuture<U> cf;
            try {
                cf = cfFactory.apply(t, index++);
                Assert.assertNotNull(cf, "cfFactory must return a non null value");
            } catch (Exception e) {
                cf = new CompletableFuture<>();
                cf.completeExceptionally(new CompletionException(e));
            }
            futures.add(cf);
        }
        return each(futures);

    }

    public static <T, U> CompletableFuture<List<U>> eachSequentially(Iterable<T> list, CFFactory<T, U> cfFactory) {
        CompletableFuture<List<U>> result = new CompletableFuture<>();
        eachSequentiallyImpl(list.iterator(), cfFactory, 0, new ArrayList<>(), result);
        return result;
    }

    private static <T, U> void eachSequentiallyImpl(Iterator<T> iterator, CFFactory<T, U> cfFactory, int index, List<U> tmpResult, CompletableFuture<List<U>> overallResult) {
        if (!iterator.hasNext()) {
            overallResult.complete(tmpResult);
        }
        CompletableFuture<U> cf;
        try {
            cf = cfFactory.apply(iterator.next(), index, tmpResult);
            Assert.assertNotNull(cf, "cfFactory must return a non null value");
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
}
