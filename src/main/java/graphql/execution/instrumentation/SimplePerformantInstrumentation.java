package graphql.execution.instrumentation;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.PublicApi;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.language.Document;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.validation.ValidationError;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static graphql.execution.instrumentation.SimpleInstrumentationContext.noOp;

/**
 * An implementation of {@link Instrumentation} that does nothing.  It can be used
 * as a base for derived classes where you only implement the methods you want to.  The reason this
 * class is designated as more performant is that it does not delegate back to the deprecated methods
 * and allocate a new state object per call.
 * <p>
 * This behavior was left in place for backwards compatibility reasons inside {@link Instrumentation}
 * and {@link SimpleInstrumentation} but has not been done in this class since no existing classes
 * could have derived from it.  If you want more performant behavior on methods you don't implement
 * then this is the base class to use, since it will not delegate back to old methods
 * and cause a new state to be allocated.
 */
@SuppressWarnings("deprecation")
@PublicApi
@NullMarked
public class SimplePerformantInstrumentation implements Instrumentation {

    /**
     * A singleton instance of a {@link Instrumentation} that does nothing
     */
    public static final SimplePerformantInstrumentation INSTANCE = new SimplePerformantInstrumentation();

    @Override
    public @Nullable CompletableFuture<InstrumentationState> createStateAsync(InstrumentationCreateStateParameters parameters) {
        InstrumentationState state = createState(parameters);
        return state == null ? null : CompletableFuture.completedFuture(state);
    }

    @Override
    public @Nullable InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return null;
    }

    @Override
    public @Nullable InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return noOp();
    }

    @Override
    public @Nullable InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return noOp();
    }

    @Override
    public @Nullable InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters, InstrumentationState state) {
        return noOp();
    }

    @Override
    public @Nullable InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
        return noOp();
    }

    @Override
    public @Nullable ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {
        return ExecutionStrategyInstrumentationContext.NOOP;
    }

    @Override
    public @Nullable ExecuteObjectInstrumentationContext beginExecuteObject(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {
        return ExecuteObjectInstrumentationContext.NOOP;
    }

    @Override
    public @Nullable InstrumentationContext<ExecutionResult> beginSubscribedFieldEvent(InstrumentationFieldParameters parameters, InstrumentationState state) {
        return noOp();
    }

    @Override
    public @Nullable InstrumentationContext<Object> beginFieldExecution(InstrumentationFieldParameters parameters, InstrumentationState state) {
        return noOp();
    }

    @Override
    public @Nullable InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        return noOp();
    }

    @Override
    public @Nullable InstrumentationContext<Object> beginFieldCompletion(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        return noOp();
    }

    @Override
    public @Nullable InstrumentationContext<Object> beginFieldListCompletion(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        return noOp();
    }

    @Override
    public @NonNull ExecutionInput instrumentExecutionInput(ExecutionInput executionInput, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return executionInput;
    }

    @Override
    public @NonNull DocumentAndVariables instrumentDocumentAndVariables(DocumentAndVariables documentAndVariables, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return documentAndVariables;
    }

    @Override
    public @NonNull GraphQLSchema instrumentSchema(GraphQLSchema schema, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return schema;
    }

    @Override
    public @NonNull ExecutionContext instrumentExecutionContext(ExecutionContext executionContext, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return executionContext;
    }

    @Override
    public @NonNull DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        return dataFetcher;
    }

    @Override
    public @NonNull CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return CompletableFuture.completedFuture(executionResult);
    }
}
