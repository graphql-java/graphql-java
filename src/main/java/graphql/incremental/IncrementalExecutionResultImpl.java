package graphql.incremental;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.ExperimentalApi;
import org.reactivestreams.Publisher;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@ExperimentalApi
public class IncrementalExecutionResultImpl extends ExecutionResultImpl implements IncrementalExecutionResult {
    private final boolean hasNext;
    private final List<IncrementalPayload> incremental;
    private final Publisher<DelayedIncrementalExecutionResult> incrementalItemPublisher;

    private IncrementalExecutionResultImpl(Builder builder) {
        super(builder);
        this.hasNext = builder.hasNext;
        this.incremental = builder.incremental;
        this.incrementalItemPublisher = builder.incrementalItemPublisher;
    }

    @Override
    public boolean hasNext() {
        return this.hasNext;
    }

    @Nullable
    @Override
    public List<IncrementalPayload> getIncremental() {
        return this.incremental;
    }

    @Override
    public Publisher<DelayedIncrementalExecutionResult> getIncrementalItemPublisher() {
        return incrementalItemPublisher;
    }

    /**
     * @return a {@link Builder} that can be used to create an instance of {@link IncrementalExecutionResultImpl}
     */
    public static Builder newIncrementalExecutionResult() {
        return new Builder();
    }

    public static Builder fromExecutionResult(ExecutionResult executionResult) {
        return new Builder().from(executionResult);
    }

    @Override
    public Map<String, Object> toSpecification() {
        Map<String, Object> map = new LinkedHashMap<>(super.toSpecification());
        map.put("hasNext", hasNext);

        if (this.incremental != null) {
            map.put("incremental",
                    this.incremental.stream()
                            .map(IncrementalPayload::toSpecification)
                            .collect(Collectors.toCollection(LinkedList::new))
            );
        }

        return map;
    }

    public static class Builder extends ExecutionResultImpl.Builder<Builder> {
        private boolean hasNext = true;
        public List<IncrementalPayload> incremental;
        private Publisher<DelayedIncrementalExecutionResult> incrementalItemPublisher;

        public Builder hasNext(boolean hasNext) {
            this.hasNext = hasNext;
            return this;
        }

        public Builder incremental(List<IncrementalPayload> incremental) {
            this.incremental = incremental;
            return this;
        }

        public Builder incrementalItemPublisher(Publisher<DelayedIncrementalExecutionResult> incrementalItemPublisher) {
            this.incrementalItemPublisher = incrementalItemPublisher;
            return this;
        }

        public Builder from(IncrementalExecutionResult incrementalExecutionResult) {
            super.from(incrementalExecutionResult);
            this.hasNext = incrementalExecutionResult.hasNext();
            return this;
        }

        public IncrementalExecutionResult build() {
            return new IncrementalExecutionResultImpl(this);
        }
    }
}
