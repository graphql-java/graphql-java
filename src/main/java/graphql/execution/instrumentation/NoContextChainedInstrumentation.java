package graphql.execution.instrumentation;

import graphql.ExecutionResult;
import graphql.PublicApi;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationReactiveResultsParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.language.Document;
import graphql.validation.ValidationError;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * This version of {@link ChainedInstrumentation} will call a list of {@link Instrumentation}s
 * but it will never back on the returned {@link InstrumentationContext} objects, hence it is only suitable to
 * certain use cases.
 *
 * Only use this class if you are optimising for memory usage as scale.  In most cases the {@link ChainedInstrumentation}
 * will do the job required with all the instrumentation features used however some users require the fastest performance and lowest memory
 * usage at scale and this class can be used.
 *
 * At scale, the fact that the graphql engine holds the {@link InstrumentationContext} objects in memory for a (relatively) long time
 * (the length of the request or the length of a large field fetch) means that memory pressure can grow
 * and objects move into longer tenure GC pools.  Holding these contexts is also not necessary if the instrumentation never needs to know when a
 * certain execution step finishes.
 *
 * The {@link InstrumentationContext} is used ot know when an execution step has completed, so instrumentations that do
 * timings say need to use this callback mechanism.  Putting such an instrumentation into {@link NoContextChainedInstrumentation} would
 * be a mistake because no callback will occur.  Therefore, use of this class is reserved for very specific us cases.  You are fore-warned.
 *
 * This class never holds onto the returned {@link InstrumentationContext} objects and always returns null
 * as itself.
 */
@PublicApi
@NullMarked
public class NoContextChainedInstrumentation extends ChainedInstrumentation {

    public NoContextChainedInstrumentation(List<Instrumentation> instrumentations) {
        super(instrumentations);
    }

    public NoContextChainedInstrumentation(Instrumentation... instrumentations) {
        super(instrumentations);
    }

    private <T> @Nullable T runAll(InstrumentationState state, BiConsumer<Instrumentation, InstrumentationState> stateConsumer) {
        chainedConsume(state, stateConsumer);
        return null;
    }

    @Override
    public @Nullable InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return runAll(state, (instrumentation, specificState) -> instrumentation.beginExecution(parameters, specificState));
    }

    @Override
    public @Nullable InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return runAll(state, (instrumentation, specificState) -> instrumentation.beginParse(parameters, specificState));
    }

    @Override
    public @Nullable InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters, InstrumentationState state) {
        return runAll(state, (instrumentation, specificState) -> instrumentation.beginValidation(parameters, specificState));
    }

    @Override
    public @Nullable InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
        return runAll(state, (instrumentation, specificState) -> instrumentation.beginExecuteOperation(parameters, specificState));
    }

    @Override
    public @Nullable InstrumentationContext<Void> beginReactiveResults(InstrumentationReactiveResultsParameters parameters, InstrumentationState state) {
        return runAll(state, (instrumentation, specificState) -> instrumentation.beginReactiveResults(parameters, specificState));
    }

    @Override
    public @Nullable ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {
        return runAll(state, (instrumentation, specificState) -> instrumentation.beginExecutionStrategy(parameters, specificState));
    }

    @Override
    public @Nullable ExecuteObjectInstrumentationContext beginExecuteObject(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {
        return runAll(state, (instrumentation, specificState) -> instrumentation.beginExecuteObject(parameters, specificState));
    }

    @Override
    public @Nullable InstrumentationContext<Object> beginDeferredField(InstrumentationFieldParameters parameters, InstrumentationState state) {
        return runAll(state, (instrumentation, specificState) -> instrumentation.beginDeferredField(parameters, specificState));
    }

    @Override
    public @Nullable InstrumentationContext<ExecutionResult> beginSubscribedFieldEvent(InstrumentationFieldParameters parameters, InstrumentationState state) {
        return runAll(state, (instrumentation, specificState) -> instrumentation.beginSubscribedFieldEvent(parameters, specificState));
    }

    @Override
    public @Nullable InstrumentationContext<Object> beginFieldExecution(InstrumentationFieldParameters parameters, InstrumentationState state) {
        return runAll(state, (instrumentation, specificState) -> instrumentation.beginFieldExecution(parameters, specificState));
    }

    @Override
    public @Nullable InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        return runAll(state, (instrumentation, specificState) -> instrumentation.beginFieldFetch(parameters, specificState));
    }

    @Override
    public @Nullable FieldFetchingInstrumentationContext beginFieldFetching(InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        return runAll(state, (instrumentation, specificState) -> instrumentation.beginFieldFetching(parameters, specificState));
    }

    @Override
    public @Nullable InstrumentationContext<Object> beginFieldCompletion(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        return runAll(state, (instrumentation, specificState) -> instrumentation.beginFieldCompletion(parameters, specificState));
    }

    @Override
    public @Nullable InstrumentationContext<Object> beginFieldListCompletion(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        return runAll(state, (instrumentation, specificState) -> instrumentation.beginFieldListCompletion(parameters, specificState));
    }

    // relies on the other methods from ChainedInstrumentation which this does not change
}
