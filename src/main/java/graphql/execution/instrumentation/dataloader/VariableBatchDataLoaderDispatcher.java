package graphql.execution.instrumentation.dataloader;

import graphql.Assert;
import graphql.Internal;
import org.dataloader.DataLoaderRegistry;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Internal
@NullMarked
public final class VariableBatchDataLoaderDispatcher {
    private final DataLoaderRegistry registry;
    private final Consumer<Throwable> failureHandler;
    private final List<CompletableFuture<Void>> waiting = new ArrayList<>();
    private int holds;
    private boolean flushing;

    public VariableBatchDataLoaderDispatcher(DataLoaderRegistry registry, Consumer<Throwable> failureHandler) {
        this.registry = registry;
        this.failureHandler = failureHandler;
    }

    public DataLoaderRegistry getRegistry() {
        return registry;
    }

    public synchronized void pause() {
        holds++;
    }

    public void resume() {
        synchronized (this) {
            Assert.assertTrue(holds > 0);
            holds--;
        }
        flush();
    }

    public CompletableFuture<Void> dispatch() {
        CompletableFuture<Void> result = new CompletableFuture<>();
        synchronized (this) {
            waiting.add(result);
        }
        flush();
        return result;
    }

    private void flush() {
        List<CompletableFuture<Void>> wave = takeWave();
        while (!wave.isEmpty()) {
            dispatchWave(wave);
            synchronized (this) {
                flushing = false;
            }
            wave = takeWave();
        }
    }

    private synchronized List<CompletableFuture<Void>> takeWave() {
        if (holds > 0 || flushing || waiting.isEmpty()) {
            return List.of();
        }
        flushing = true;
        List<CompletableFuture<Void>> wave = new ArrayList<>(waiting);
        waiting.clear();
        return wave;
    }

    private void dispatchWave(List<CompletableFuture<Void>> wave) {
        try {
            List<CompletableFuture<?>> dispatched = new ArrayList<>();
            registry.getDataLoaders().forEach(loader -> dispatched.add(loader.dispatch()));
            CompletableFuture.allOf(dispatched.toArray(new CompletableFuture<?>[0]))
                    .whenComplete((ignored, failure) -> completeWave(wave, failure));
        } catch (Throwable failure) {
            completeWave(wave, failure);
            failureHandler.accept(failure);
        }
    }

    private void completeWave(List<CompletableFuture<Void>> wave, @Nullable Throwable failure) {
        for (CompletableFuture<Void> result : wave) {
            if (failure == null) {
                result.complete(null);
            } else {
                result.completeExceptionally(failure);
            }
        }
    }
}
