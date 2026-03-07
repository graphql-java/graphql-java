package graphql.execution;

import graphql.Internal;
import graphql.PublicApi;
import graphql.execution.incremental.AlternativeCallContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;

/**
 * The parameters that are passed to execution strategies
 */
@PublicApi
@NullMarked
public class ExecutionStrategyParameters {
    private final ExecutionStepInfo executionStepInfo;
    private final @Nullable Object source;
    private final @Nullable Object localContext;
    private final MergedSelectionSet fields;
    private final NonNullableFieldValidator nonNullableFieldValidator;
    private final ResultPath path;
    private final @Nullable MergedField currentField;
    private final @Nullable ExecutionStrategyParameters parent;
    private final @Nullable AlternativeCallContext alternativeCallContext;

    private ExecutionStrategyParameters(ExecutionStepInfo executionStepInfo,
                                        @Nullable Object source,
                                        @Nullable Object localContext,
                                        MergedSelectionSet fields,
                                        NonNullableFieldValidator nonNullableFieldValidator,
                                        ResultPath path,
                                        @Nullable MergedField currentField,
                                        @Nullable ExecutionStrategyParameters parent,
                                        @Nullable AlternativeCallContext alternativeCallContext) {

        this.executionStepInfo = assertNotNull(executionStepInfo, "executionStepInfo is null");
        this.localContext = localContext;
        this.fields = assertNotNull(fields, "fields is null");
        this.source = source;
        this.nonNullableFieldValidator = assertNotNull(nonNullableFieldValidator, "requires a NonNullValidator");;
        this.path = path;
        this.currentField = currentField;
        this.parent = parent;
        this.alternativeCallContext = alternativeCallContext;
    }

    public ExecutionStepInfo getExecutionStepInfo() {
        return executionStepInfo;
    }

    public @Nullable Object getSource() {
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

    public @Nullable Object getLocalContext() {
        return localContext;
    }

    public @Nullable ExecutionStrategyParameters getParent() {
        return parent;
    }

    /**
     * Returns the deferred call context if we're in the scope of a deferred call.
     * A new DeferredCallContext is created for each @defer block, and is passed down to all fields within the deferred call.
     *
     * <pre>
     *     query {
     *        ... @defer {
     *            field1 {        # new DeferredCallContext created here
     *                field1a     # DeferredCallContext passed down to this field
     *            }
     *        }
     *
     *        ... @defer {
     *            field2          # new DeferredCallContext created here
     *        }
     *     }
     * </pre>
     *
     * @return the deferred call context or null if we're not in the scope of a deferred call
     */
    @Nullable
    @Internal
    public AlternativeCallContext getDeferredCallContext() {
        return alternativeCallContext;
    }

    /**
     * Returns true if we're in the scope of a deferred call.
     *
     * @return true if we're in the scope of a deferred call
     */
    public boolean isInDeferredContext() {
        return alternativeCallContext != null;
    }

    /**
     * This returns the current field in its query representations.
     *
     * @return the current merged fields
     */
    public @Nullable MergedField getField() {
        return currentField;
    }

    @Internal
    ExecutionStrategyParameters transform(MergedField currentField,
                                          ResultPath path) {
        return new ExecutionStrategyParameters(executionStepInfo,
                source,
                localContext,
                fields,
                nonNullableFieldValidator,
                path,
                currentField,
                parent,
                alternativeCallContext);
    }

    @Internal
    ExecutionStrategyParameters transform(ExecutionStepInfo executionStepInfo,
                                          MergedSelectionSet fields,
                                          @Nullable Object source) {
        return new ExecutionStrategyParameters(executionStepInfo,
                source,
                localContext,
                fields,
                nonNullableFieldValidator,
                path,
                currentField,
                parent,
                alternativeCallContext);
    }

    @Internal
    ExecutionStrategyParameters transform(ExecutionStepInfo executionStepInfo,
                                          ResultPath path,
                                          @Nullable Object localContext,
                                          @Nullable Object source) {
        return new ExecutionStrategyParameters(executionStepInfo,
                source,
                localContext,
                fields,
                nonNullableFieldValidator,
                path,
                currentField,
                parent,
                alternativeCallContext);
    }

    @Internal
    ExecutionStrategyParameters transform(ExecutionStepInfo executionStepInfo,
                                          @Nullable Object localContext,
                                          @Nullable Object source) {
        return new ExecutionStrategyParameters(executionStepInfo,
                source,
                localContext,
                fields,
                nonNullableFieldValidator,
                path,
                currentField,
                parent,
                alternativeCallContext);
    }

    @Internal
    ExecutionStrategyParameters transform(MergedField currentField,
                                          ResultPath path,
                                          ExecutionStrategyParameters parent) {
        return new ExecutionStrategyParameters(executionStepInfo,
                source,
                localContext,
                fields,
                nonNullableFieldValidator,
                path,
                currentField,
                parent,
                alternativeCallContext);
    }

    public ExecutionStrategyParameters transform(Consumer<Builder> builderConsumer) {
        Builder builder = newParameters(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @Override
    public String toString() {
        return String.format("ExecutionStrategyParameters { path=%s, executionStepInfo=%s, source=%s, fields=%s }",
                path, executionStepInfo, source, fields);
    }

    public static Builder newParameters() {
        return new Builder();
    }

    public static Builder newParameters(ExecutionStrategyParameters oldParameters) {
        return new Builder(oldParameters);
    }

    @NullUnmarked
    public static class Builder {
        ExecutionStepInfo executionStepInfo;
        Object source;
        Object localContext;
        MergedSelectionSet fields;
        NonNullableFieldValidator nonNullableFieldValidator;
        ResultPath path = ResultPath.rootPath();
        MergedField currentField;
        ExecutionStrategyParameters parent;
        AlternativeCallContext alternativeCallContext;

        /**
         * @see ExecutionStrategyParameters#newParameters()
         */
        private Builder() {
        }

        /**
         * @see ExecutionStrategyParameters#newParameters(ExecutionStrategyParameters)
         */
        private Builder(ExecutionStrategyParameters oldParameters) {
            this.executionStepInfo = oldParameters.executionStepInfo;
            this.source = oldParameters.source;
            this.localContext = oldParameters.localContext;
            this.fields = oldParameters.fields;
            this.nonNullableFieldValidator = oldParameters.nonNullableFieldValidator;
            this.currentField = oldParameters.currentField;
            this.alternativeCallContext = oldParameters.alternativeCallContext;
            this.path = oldParameters.path;
            this.parent = oldParameters.parent;
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
            this.nonNullableFieldValidator = assertNotNull(nonNullableFieldValidator, "requires a NonNullValidator");
            return this;
        }

        public Builder path(ResultPath path) {
            this.path = path;
            return this;
        }

        public Builder parent(ExecutionStrategyParameters parent) {
            this.parent = parent;
            return this;
        }

        public Builder deferredCallContext(AlternativeCallContext alternativeCallContext) {
            this.alternativeCallContext = alternativeCallContext;
            return this;
        }

        public ExecutionStrategyParameters build() {
            return new ExecutionStrategyParameters(executionStepInfo, source, localContext, fields, nonNullableFieldValidator, path, currentField, parent, alternativeCallContext);
        }
    }
}
