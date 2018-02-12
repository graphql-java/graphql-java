package graphql.execution.instrumentation.export;

import graphql.execution.ExecutionStrategyParameters;

/**
 * The environment passed to each {@link graphql.execution.instrumentation.export.ExportedVariablesCollector}
 */
public class ExportedVariablesCollectionEnvironment {

    private final String variableName;
    private final Object variableValue;
    private final ExecutionStrategyParameters executionStrategyParameters;

    ExportedVariablesCollectionEnvironment(String variableName, Object variableValue, ExecutionStrategyParameters executionStrategyParameters) {
        this.variableName = variableName;
        this.variableValue = variableValue;
        this.executionStrategyParameters = executionStrategyParameters;
    }

    public String getVariableName() {
        return variableName;
    }

    @SuppressWarnings("unchecked")
    public <T> T getVariableValue() {
        return (T) variableValue;
    }

    public ExecutionStrategyParameters getExecutionStrategyParameters() {
        return executionStrategyParameters;
    }

    public static Builder newCollectionEnvironment() {
        return new Builder();
    }

    public static class Builder {
        private String variableName;
        private Object variableValue;
        private ExecutionStrategyParameters executionStrategyParameters;

        public Builder variableName(String variableName) {
            this.variableName = variableName;
            return this;
        }

        public Builder variableValue(Object variableValue) {
            this.variableValue = variableValue;
            return this;
        }

        public Builder executionStrategyParameters(ExecutionStrategyParameters executionStrategyParameters) {
            this.executionStrategyParameters = executionStrategyParameters;
            return this;
        }

        public ExportedVariablesCollectionEnvironment build() {
            return new ExportedVariablesCollectionEnvironment(variableName, variableValue, executionStrategyParameters);
        }


    }
}
