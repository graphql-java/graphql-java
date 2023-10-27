package graphql.util;

import graphql.Internal;

import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

/**
 * This provides reentrant locking support for our code base.  Future worlds like Loom virtual threads don't like
 * synchronised calls since they pin the VT to the carrier thread.  Word on the street is that locks are preferred
 * to synchronised.
 */
@Internal
public class LockKit {

    /**
     * A class to run code inside a reentrant lock
     */
    public static class ReentrantLock {
        private final Lock lock = new java.util.concurrent.locks.ReentrantLock();

        /**
         * Sometimes you need to directly lock things like for checked exceptions
         * <p>
         * It's on you to unlock it!
         */
        public void lock() {
            lock.lock();
        }

        public void unlock() {
            lock.unlock();
        }

        public void runLocked(Runnable codeToRun) {
            lock.lock();
            try {
                codeToRun.run();
            } finally {
                lock.unlock();
            }
        }

        public <E> E callLocked(Supplier<E> codeToRun) {
            lock.lock();
            try {
                return codeToRun.get();
            } finally {
                lock.unlock();
            }
        }
    }


    /**
     * Will allow for lazy computation of some values just once
     */
    public static class ComputedOnce {

        private volatile boolean beenComputed = false;
        private final ReentrantLock lock = new ReentrantLock();


        public boolean hasBeenComputed() {
            return beenComputed;
        }

        public void runOnce(Runnable codeToRunOnce) {
            if (beenComputed) {
                return;
            }
            lock.runLocked(() -> {
                // double lock check
                if (beenComputed) {
                    return;
                }
                try {
                    codeToRunOnce.run();
                    beenComputed = true;
                } finally {
                    // even for exceptions we will say its computed
                    beenComputed = true;
                }
            });
        }
    }
}
