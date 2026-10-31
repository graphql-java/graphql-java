package graphql;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;

import java.util.function.Consumer;

/** An individual execution result and its position in the input variables list. */
@ExperimentalApi
@NullMarked
public final class VariableBatchResult {
    private final int variableIndex;
    private final ExecutionResult executionResult;

    private VariableBatchResult(Builder builder) {
        Assert.assertTrue(builder.variableIndex >= 0, () -> "variableIndex must be non-negative");
        variableIndex = builder.variableIndex;
        executionResult = Assert.assertNotNull(builder.executionResult);
    }

    public int getVariableIndex() {
        return variableIndex;
    }

    public ExecutionResult getExecutionResult() {
        return executionResult;
    }

    public static Builder newVariableBatchResult() {
        return new Builder();
    }

    public VariableBatchResult transform(Consumer<Builder> consumer) {
        Builder builder = newVariableBatchResult().variableIndex(variableIndex).executionResult(executionResult);
        consumer.accept(builder);
        return builder.build();
    }

    @NullUnmarked
    public static final class Builder {
        private int variableIndex;
        private ExecutionResult executionResult;

        public Builder variableIndex(int variableIndex) {
            this.variableIndex = variableIndex;
            return this;
        }

        public Builder executionResult(ExecutionResult executionResult) {
            this.executionResult = executionResult;
            return this;
        }

        public VariableBatchResult build() {
            return new VariableBatchResult(this);
        }
    }
}
