package graphql.execution.instrumentation.dataloader;

import graphql.ExperimentalApi;
import graphql.Internal;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;


@ExperimentalApi
public final class ChainedDataLoader<T, U> extends CompletableFuture<U> {

    private final Function<? super T, ? extends CompletionStage<U>> applyFunction;
    private final CompletableFuture<T> firstDataLoaderResult;
    private final AtomicReference<CompletableFuture<U>> secondDataLoaderResult = new AtomicReference<>();

    private final AtomicBoolean secondDataLoaderStarted = new AtomicBoolean(false);

    private AtomicReference<Runnable> runWhenSecondDataLoaderHasCalled = new AtomicReference<>();

    private ChainedDataLoader(CompletableFuture<T> firstDataLoaderResult, Function<? super T, ? extends CompletableFuture<U>> applyFunction) {
        this.firstDataLoaderResult = firstDataLoaderResult;
        this.applyFunction = applyFunction;

        firstDataLoaderResult.whenComplete((t, throwable) -> {
            if (throwable != null) {
                return;
            }
            CompletableFuture<U> secondCF = applyFunction.apply(t);
            secondDataLoaderResult.set(secondCF);
            Runnable runnable = runWhenSecondDataLoaderHasCalled.get();
            if (runnable != null) {
                runnable.run();
            }
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

    @Internal
    public void runWhenSecondDataLoaderHasCalled(Runnable runnable) {
        runWhenSecondDataLoaderHasCalled.set(runnable);
    }

    public boolean secondDataLoaderCalled() {
        return secondDataLoaderResult.get() != null;
    }

    @ExperimentalApi
    public static <T, U> ChainedDataLoader<T, U> two(CompletableFuture<T> firstDataLoaderResult, Function<? super T, ? extends CompletableFuture<U>> applyFunction) {
        return new ChainedDataLoader<>(firstDataLoaderResult, applyFunction);
    }

}
