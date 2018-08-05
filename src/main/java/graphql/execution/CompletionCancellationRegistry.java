package graphql.execution;

import graphql.Internal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Internal
public class CompletionCancellationRegistry {
    private final List<Runnable> callbacks = new ArrayList<>();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public CompletionCancellationRegistry(CompletionCancellationRegistry parent) {
        parent.addCancellationCallback(this::dispatch);
    }

    public CompletionCancellationRegistry() {
    }

    public void addCancellationCallback(Runnable callback) {
        synchronized (callbacks) {
            if (cancelled.get()) {
                callback.run();
            } else {
                callbacks.add(callback);
            }
        }
    }

    public void dispatch() {
        cancelled.set(true);
        synchronized (callbacks) {
            callbacks.forEach(Runnable::run);
            callbacks.clear();
        }
    }
}
