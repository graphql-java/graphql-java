package graphql.execution.lazy;

import graphql.Internal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Internal
public class LazySupport {
    private final AtomicBoolean lazyFieldsDetected = new AtomicBoolean();
    private final AtomicInteger numPendingCompletions = new AtomicInteger();
    private final List<Runnable> callbacks = new ArrayList<>();

    public class Token {
        private final AtomicBoolean released = new AtomicBoolean(false);

        public void release() {
            if (!released.getAndSet(true)) {
                if (numPendingCompletions.decrementAndGet() == 0) {
                    synchronized (callbacks) {
                        List<Runnable> tmp = new ArrayList<>(callbacks);
                        callbacks.clear();
                        tmp.forEach(Runnable::run);
                    }
                }
            }
        }
    }

    public Token addLazyCompletion() {
        lazyFieldsDetected.set(true);
        numPendingCompletions.incrementAndGet();
        return new Token();
    }

    public boolean hasPendingCompletions() {
        return numPendingCompletions.get() != 0;
    }

    public boolean lazyFieldsDetected() {
        return lazyFieldsDetected.get();
    }

    public void addCompletionCallback(Runnable callback) {
        synchronized (callbacks) {
            if (hasPendingCompletions()) {
                callbacks.add(callback);
            } else {
                callback.run();
            }
        }
    }
}
