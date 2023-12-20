package graphql.defer;

import java.util.Collections;
import java.util.List;

public class IncrementalExecutionResultImpl implements IncrementalExecutionResult {
    private final List<IncrementalItem> incrementalItems;
    private final String label;
    private final boolean hasNext;

    private IncrementalExecutionResultImpl(List<IncrementalItem> incrementalItems, String label, boolean hasNext) {
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

        public IncrementalExecutionResultImpl.Builder hasNext(boolean hasNext) {
            this.hasNext = hasNext;
            return this;
        }

        public IncrementalExecutionResultImpl.Builder incrementalItems(List<IncrementalItem> incrementalItems) {
            this.incrementalItems = incrementalItems;
            return this;
        }

        public IncrementalExecutionResultImpl.Builder label(String label) {
            this.label = label;
            return this;
        }

        public IncrementalExecutionResultImpl build() {
            return new IncrementalExecutionResultImpl(this.incrementalItems, this.label, this.hasNext);
        }
    }
}
