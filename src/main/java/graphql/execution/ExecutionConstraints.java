package graphql.execution;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This represents constraints on the execution of queries to help protect against
 * large or malicious queries
 */
public class ExecutionConstraints {
    //
    // future versions of this class could have "query complexity" etc. in it
    // but for now we just have query depth
    //
    private final int maxQueryDepth;
    private final AtomicInteger queryDepth;

    private ExecutionConstraints(int maxQueryDepth) {
        this.maxQueryDepth = maxQueryDepth;
        this.queryDepth = new AtomicInteger(0);
    }

    public int getMaxQueryDepth() {
        return maxQueryDepth;
    }

    public int getQueryDepth() {
        return queryDepth.get();
    }

    public boolean hasExceededMaxDepthConstraint() {
        // only check if we have positive max depth in play
        if (maxQueryDepth > 0) {
            if (queryDepth.get() > maxQueryDepth) {
                return true;
            }
        }
        return false;
    }

    /**
     * This is for {@link ExecutionStrategy}s to call to run a query and have the query depth incremented and decremented for you safely.
     *
     * @param code the code to call
     * @param <T>  for two and two for T
     *
     * @return the result
     */
    public <T> T callTrackingDepth(Callable<T> code) {
        try {
            queryDepth.incrementAndGet();
            return code.call();
        } catch (Exception e) {
            // callable forces us to do this because it mandates Exceptions in its signature
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        } finally {
            queryDepth.decrementAndGet();
        }
    }

    @Override
    public String toString() {
        return "maxQueryDepth=" + maxQueryDepth +
                ", queryDepth=" + queryDepth.get() +
                '}';
    }


    public static Builder newConstraints() {
        return new Builder();
    }

    public static class Builder {
        private int maxQueryDepth = -1; // by default no constraint on depth

        public Builder maxQueryDepth(int maxQueryDepth) {
            this.maxQueryDepth = maxQueryDepth;
            return this;
        }

        public ExecutionConstraints build() {
            return new ExecutionConstraints(maxQueryDepth);
        }
    }

}
