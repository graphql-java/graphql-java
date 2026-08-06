package graphql.incremental;

import graphql.ExperimentalApi;
import graphql.util.FpKit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ExperimentalApi
@NullMarked
public class DelayedIncrementalPartialResultImpl implements DelayedIncrementalPartialResult {
    private final @Nullable List<IncrementalPayload> incrementalItems;
    private final boolean hasNext;
    private final @Nullable Map<Object, Object> extensions;

    private DelayedIncrementalPartialResultImpl(Builder builder) {
        this.incrementalItems = builder.incrementalItems;
        this.hasNext = builder.hasNext;
        this.extensions = builder.extensions;
    }

    @Override
    public @Nullable List<IncrementalPayload> getIncremental() {
        return this.incrementalItems;
    }

    @Override
    public boolean hasNext() {
        return this.hasNext;
    }

    @Override
    public @Nullable Map<Object, Object> getExtensions() {
        return this.extensions;
    }

    @Override
    public Map<String, Object> toSpecification() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hasNext", hasNext);

        if (extensions != null) {
            result.put("extensions", extensions);
        }

        if (incrementalItems != null) {
            List<Map<String, Object>> list = FpKit.arrayListSizedTo(incrementalItems);
            for (IncrementalPayload incrementalItem : incrementalItems) {
                list.add(incrementalItem.toSpecification());
            }
            result.put("incremental", list);
        }

        return result;
    }

    /**
     * @return a {@link Builder} that can be used to create an instance of {@link DelayedIncrementalPartialResultImpl}
     */
    public static Builder newIncrementalExecutionResult() {
        return new Builder();
    }

    @NullUnmarked
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

        public Builder extensions(Map<Object, Object> extensions) {
            this.extensions = extensions;
            return this;
        }

        public DelayedIncrementalPartialResultImpl build() {
            return new DelayedIncrementalPartialResultImpl(this);
        }
    }
}
