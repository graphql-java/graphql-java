package graphql.incremental;

import graphql.ExperimentalApi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Override
    public Map<String, Object> toSpecification() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hasNext", hasNext);

        if (extensions != null) {
            result.put("extensions", extensions);
        }

        if(incrementalItems != null) {
            result.put("incremental", incrementalItems.stream()
                    .map(IncrementalPayload::toSpecification)
                    .collect(Collectors.toList()));
        }

        return result;
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
