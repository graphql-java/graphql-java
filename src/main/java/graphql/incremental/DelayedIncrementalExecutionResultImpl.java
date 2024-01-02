package graphql.incremental;

import java.util.Collections;
import java.util.List;

public class DelayedIncrementalExecutionResultImpl implements DelayedIncrementalExecutionResult {
    private final List<IncrementalItem> incrementalItems;
    private final String label;
    private final boolean hasNext;

    private DelayedIncrementalExecutionResultImpl(List<IncrementalItem> incrementalItems, String label, boolean hasNext) {
        this.incrementalItems = incrementalItems;
        this.label = label;
        this.hasNext = hasNext;
    }

    @Override
    public List<IncrementalItem> getIncremental() {
        return this.incrementalItems;
    }

    @Override
    public String getLabel() {
        return this.label;
    }

    @Override
    public boolean hasNext() {
        return this.hasNext;
    }

    public static Builder newIncrementalExecutionResult() {
        return new Builder();
    }

    public static class Builder {
        private boolean hasNext = false;
        private List<IncrementalItem> incrementalItems = Collections.emptyList();
        private String label = null;

        public DelayedIncrementalExecutionResultImpl.Builder hasNext(boolean hasNext) {
            this.hasNext = hasNext;
            return this;
        }

        public DelayedIncrementalExecutionResultImpl.Builder incrementalItems(List<IncrementalItem> incrementalItems) {
            this.incrementalItems = incrementalItems;
            return this;
        }

        public DelayedIncrementalExecutionResultImpl.Builder label(String label) {
            this.label = label;
            return this;
        }

        public DelayedIncrementalExecutionResultImpl build() {
            return new DelayedIncrementalExecutionResultImpl(this.incrementalItems, this.label, this.hasNext);
        }
    }
}
