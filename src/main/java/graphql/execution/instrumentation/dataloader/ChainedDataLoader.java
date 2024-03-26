package graphql.execution.instrumentation.dataloader;

import graphql.ExperimentalApi;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;


@ExperimentalApi
public final class ChainedDataLoader<T, U> extends CompletableFuture<U> {

    private final Function<? super T, ? extends CompletionStage<U>> applyFunction;
    private final CompletableFuture<T> firstDataLoaderResult;
    private final CompletableFuture<Void> secondDataLoaderCalled = new CompletableFuture<>();


    private ChainedDataLoader(CompletableFuture<T> firstDataLoaderResult, Function<? super T, ? extends CompletableFuture<U>> applyFunction) {
        this.firstDataLoaderResult = firstDataLoaderResult;
        this.applyFunction = applyFunction;

        firstDataLoaderResult.whenComplete((t, throwable) -> {
            if (throwable != null) {
                return;
            }
            CompletableFuture<U> secondCF = applyFunction.apply(t);
            secondDataLoaderCalled.complete(null);
            secondCF.whenComplete((finalResult, finalThrowable) -> {
                if (finalThrowable != null) {
                    super.completeExceptionally(finalThrowable);
                } else {
                    super.complete(finalResult);
                }
            });

        });
    }

    @Override
    public boolean complete(U value) {
        throw new RuntimeException("You can not call complete on a ChainedDataLoader");
    }

    @Override
    public boolean completeExceptionally(Throwable ex) {
        throw new RuntimeException("You can not call completeExceptionally on a ChainedDataLoader");
    }

    public CompletableFuture<Void> getSecondDataLoaderCalled() {
        return secondDataLoaderCalled;
    }

    @ExperimentalApi
    public static <T, U> ChainedDataLoader<T, U> two(CompletableFuture<T> firstDataLoaderResult, Function<? super T, ? extends CompletableFuture<U>> applyFunction) {
        return new ChainedDataLoader<>(firstDataLoaderResult, applyFunction);
    }

}