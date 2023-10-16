package graphql.engine;

import graphql.Assert;
import graphql.Internal;
import graphql.PublicApi;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.MergedField;
import graphql.execution.MergedSelectionSet;
import graphql.execution.NonNullableFieldValidator;
import graphql.execution.ResultPath;
import graphql.language.OperationDefinition;

import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;

/**
 * The parameters that are passed to {@link GraphQLEngine}s
 */
@PublicApi
public class EngineParameters {
    private final ExecutionStepInfo executionStepInfo;
    private final Object source;
    private final Object localContext;
    private final MergedSelectionSet fields;
    private final NonNullableFieldValidator nonNullableFieldValidator;
    private final ResultPath path;
    private final MergedField currentField;
    private final OperationDefinition.Operation operation;

    private EngineParameters(Builder builder) {

        this.executionStepInfo = assertNotNull(builder.executionStepInfo, () -> "executionStepInfo is null");
        this.localContext = builder.localContext;
        this.fields = assertNotNull(builder.fields, () -> "fields is null");
        this.source = builder.source;
        this.nonNullableFieldValidator = builder.nonNullableFieldValidator;
        this.path = builder.path;
        this.currentField = builder.currentField;
        this.operation = builder.operation;
    }

    public NonNullableFieldValidator getNonNullableFieldValidator() {
        return nonNullableFieldValidator;
    }

    public OperationDefinition.Operation getOperation() {
        return operation;
    }

    public ExecutionStepInfo getExecutionStepInfo() {
        return executionStepInfo;
    }

    public Object getSource() {
        return source;
    }

    public MergedSelectionSet getFields() {
        return fields;
    }

    public NonNullableFieldValidator getNonNullFieldValidator() {
        return nonNullableFieldValidator;
    }

    public ResultPath getPath() {
        return path;
    }

    public Object getLocalContext() {
        return localContext;
    }

    /**
     * This returns the current field in its query representations.
     *
     * @return the current merged fields
     */
    public MergedField getField() {
        return currentField;
    }

    public EngineParameters transform(Consumer<Builder> builderConsumer) {
        Builder builder = newParameters(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @Override
    public String toString() {
        return String.format("EngineParameters { path=%s, executionStepInfo=%s, source=%s, fields=%s }",
                path, executionStepInfo, source, fields);
    }

    public static Builder newParameters() {
        return new Builder();
    }

    public static Builder newParameters(EngineParameters oldParameters) {
        return new Builder(oldParameters);
    }

    @Internal
    public static class Builder {
        ExecutionStepInfo executionStepInfo;
        Object source;
        Object localContext;
        MergedSelectionSet fields;
        NonNullableFieldValidator nonNullableFieldValidator;
        ResultPath path = ResultPath.rootPath();
        MergedField currentField;
        OperationDefinition.Operation operation;

        /**
         * @see EngineParameters#newParameters()
         */
        private Builder() {
        }

        /**
         * @see EngineParameters#newParameters(EngineParameters)
         */
        private Builder(EngineParameters oldParameters) {
            this.executionStepInfo = oldParameters.executionStepInfo;
            this.source = oldParameters.source;
            this.localContext = oldParameters.localContext;
            this.fields = oldParameters.fields;
            this.nonNullableFieldValidator = oldParameters.nonNullableFieldValidator;
            this.currentField = oldParameters.currentField;
            this.path = oldParameters.path;
            this.operation = oldParameters.operation;
        }

        public Builder executionStepInfo(ExecutionStepInfo executionStepInfo) {
            this.executionStepInfo = executionStepInfo;
            return this;
        }

        public Builder executionStepInfo(ExecutionStepInfo.Builder executionStepInfoBuilder) {
            this.executionStepInfo = executionStepInfoBuilder.build();
            return this;
        }

        public Builder fields(MergedSelectionSet fields) {
            this.fields = fields;
            return this;
        }

        public Builder field(MergedField currentField) {
            this.currentField = currentField;
            return this;
        }

        public Builder source(Object source) {
            this.source = source;
            return this;
        }

        public Builder localContext(Object localContext) {
            this.localContext = localContext;
            return this;
        }

        public Builder nonNullFieldValidator(NonNullableFieldValidator nonNullableFieldValidator) {
            this.nonNullableFieldValidator = Assert.assertNotNull(nonNullableFieldValidator, () -> "requires a NonNullValidator");
            return this;
        }

        public Builder path(ResultPath path) {
            this.path = path;
            return this;
        }

        public Builder operation(OperationDefinition.Operation operation) {
            this.operation = operation;
            return this;
        }

        public EngineParameters build() {
            return new EngineParameters(this);
        }
    }
}
