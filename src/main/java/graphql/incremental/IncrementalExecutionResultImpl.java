package graphql.incremental;

import graphql.ExecutionResultImpl;
import org.reactivestreams.Publisher;

import java.util.LinkedHashMap;
import java.util.Map;

public class IncrementalExecutionResultImpl extends ExecutionResultImpl implements IncrementalExecutionResult {
    private final boolean hasNext;
    private final Publisher<DelayedIncrementalExecutionResult> incrementalItemPublisher;

    private IncrementalExecutionResultImpl(
            Builder builder
    ) {
        super(builder);
        this.hasNext = builder.hasNext;
        this.incrementalItemPublisher = builder.incrementalItemPublisher;
    }

    @Override
    public boolean hasNext() {
        return this.hasNext;
    }

    @Override
    public Publisher<DelayedIncrementalExecutionResult> getIncrementalItemPublisher() {
        return incrementalItemPublisher;
    }

    public static Builder newIncrementalExecutionResult() {
        return new Builder();
    }

    @Override
    public Map<String, Object> toSpecification() {
        Map<String, Object> map = new LinkedHashMap<>(super.toSpecification());
        map.put("hasNext", hasNext);
        return map;
    }

    public static class Builder extends ExecutionResultImpl.Builder<Builder> {
        private boolean hasNext = true;
        private Publisher<DelayedIncrementalExecutionResult> incrementalItemPublisher;

        public Builder hasNext(boolean hasNext) {
            this.hasNext = hasNext;
            return this;
        }

        public Builder incrementalItemPublisher(Publisher<DelayedIncrementalExecutionResult> incrementalItemPublisher) {
            this.incrementalItemPublisher = incrementalItemPublisher;
            return this;
        }

//        public Builder from(ExecutionResult executionResult) {
//            builder.from(executionResult);
//            return this;
//        }

        public IncrementalExecutionResult build() {
            return new IncrementalExecutionResultImpl(this);
        }
    }
}
