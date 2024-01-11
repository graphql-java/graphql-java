package graphql.incremental;

import graphql.ExperimentalApi;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@ExperimentalApi
public class DelayedIncrementalExecutionResultImpl implements DelayedIncrementalExecutionResult {
    private final List<IncrementalPayload> incrementalItems;
    private final boolean hasNext;
    private final Map<Object, Object> extensions;

    private DelayedIncrementalExecutionResultImpl(Builder builder) {
        this.incrementalItems = builder.incrementalItems;
        this.hasNext = builder.hasNext;
        this.extensions = builder.extensions;
    }

    @Override
    public List<IncrementalPayload> getIncremental() {
        return this.incrementalItems;
    }

    @Override
    public boolean hasNext() {
        return this.hasNext;
    }

    @Override
    public Map<Object, Object> getExtensions() {
        return this.extensions;
    }

    /**
     * @return a {@link Builder} that can be used to create an instance of {@link DelayedIncrementalExecutionResultImpl}
     */
    public static Builder newIncrementalExecutionResult() {
        return new Builder();
    }

    public static class Builder {
        private boolean hasNext = false;
        private List<IncrementalPayload> incrementalItems = Collections.emptyList();
        private Map<Object, Object> extensions;

        public Builder hasNext(boolean hasNext) {
            this.hasNext = hasNext;
            return this;
        }

        public Builder incrementalItems(List<IncrementalPayload> incrementalItems) {
            this.incrementalItems = incrementalItems;
            return this;
        }

        public Builder extensions(boolean hasNext) {
            this.hasNext = hasNext;
            return this;
        }

        public DelayedIncrementalExecutionResultImpl build() {
            return new DelayedIncrementalExecutionResultImpl(this);
        }
    }
}
