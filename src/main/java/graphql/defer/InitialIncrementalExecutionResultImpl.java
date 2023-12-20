package graphql.defer;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import org.reactivestreams.Publisher;

import java.util.LinkedHashMap;
import java.util.Map;

public class InitialIncrementalExecutionResultImpl extends ExecutionResultImpl implements InitialIncrementalExecutionResult {
    private final boolean hasNext;
    private final Publisher<IncrementalItem> incrementalItemPublisher;

    private InitialIncrementalExecutionResultImpl(
            boolean hasNext,
            Publisher<IncrementalItem> incrementalItemPublisher,
            ExecutionResultImpl other
    ) {
        super(other);
        this.hasNext = hasNext;
        this.incrementalItemPublisher = incrementalItemPublisher;
    }

    @Override
    public boolean hasNext() {
        return this.hasNext;
    }

    @Override
    public Publisher<IncrementalItem> getIncrementalItemPublisher() {
        return incrementalItemPublisher;
    }

    public static Builder newInitialIncrementalExecutionResult() {
        return new Builder();
    }

    @Override
    public Map<String, Object> toSpecification() {
        Map<String, Object> map = new LinkedHashMap<>(super.toSpecification());
        map.put("hasNext", hasNext);
        return map;
    }

    public static class Builder {
        private boolean hasNext = true;
        private Publisher<IncrementalItem> incrementalItemPublisher;
        private ExecutionResultImpl.Builder builder = ExecutionResultImpl.newExecutionResult();

        public Builder hasNext(boolean hasNext) {
            this.hasNext = hasNext;
            return this;
        }

        public Builder incrementalItemPublisher(Publisher<IncrementalItem> incrementalItemPublisher) {
            this.incrementalItemPublisher = incrementalItemPublisher;
            return this;
        }

        public Builder from(ExecutionResult executionResult) {
            builder.from(executionResult);
            return this;
        }

        public InitialIncrementalExecutionResult build() {
            ExecutionResultImpl build = (ExecutionResultImpl) builder.build();
            return new InitialIncrementalExecutionResultImpl(this.hasNext, this.incrementalItemPublisher, build);
        }
    }
}
