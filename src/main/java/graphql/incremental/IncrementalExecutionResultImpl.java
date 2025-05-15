package graphql.incremental;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.ExperimentalApi;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@ExperimentalApi
public class IncrementalExecutionResultImpl extends ExecutionResultImpl implements IncrementalExecutionResult {
    private final boolean hasNext;
    private final List<IncrementalPayload> incremental;
    private final Publisher<DelayedIncrementalPartialResult> incrementalItemPublisher;

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
    public Publisher<DelayedIncrementalPartialResult> getIncrementalItemPublisher() {
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
    public IncrementalExecutionResult transform(Consumer<ExecutionResult.Builder<?>> builderConsumer) {
        var builder = fromExecutionResult(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @Override
    public Map<String, Object> toSpecification() {
        Map<String, Object> map = new LinkedHashMap<>(super.toSpecification());
        map.put("hasNext", hasNext);

        if (this.incremental != null) {
            LinkedList<Map<String, Object>> linkedList = new LinkedList<>();
            for (IncrementalPayload incrementalPayload : this.incremental) {
                linkedList.add(incrementalPayload.toSpecification());
            }
            map.put("incremental", linkedList);
        }

        return map;
    }

    public static class Builder extends ExecutionResultImpl.Builder<Builder> {
        private boolean hasNext = true;
        public List<IncrementalPayload> incremental;
        private Publisher<DelayedIncrementalPartialResult> incrementalItemPublisher;

        public Builder hasNext(boolean hasNext) {
            this.hasNext = hasNext;
            return this;
        }

        public Builder incremental(List<IncrementalPayload> incremental) {
            this.incremental = incremental;
            return this;
        }

        public Builder incrementalItemPublisher(Publisher<DelayedIncrementalPartialResult> incrementalItemPublisher) {
            this.incrementalItemPublisher = incrementalItemPublisher;
            return this;
        }

        public Builder from(IncrementalExecutionResult incrementalExecutionResult) {
            super.from(incrementalExecutionResult);
            this.hasNext = incrementalExecutionResult.hasNext();
            this.incremental = incrementalExecutionResult.getIncremental();
            this.incrementalItemPublisher = incrementalExecutionResult.getIncrementalItemPublisher();
            return this;
        }

        public IncrementalExecutionResult build() {
            return new IncrementalExecutionResultImpl(this);
        }
    }
}
