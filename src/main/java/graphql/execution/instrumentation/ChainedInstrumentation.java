package graphql.execution.instrumentation;

import com.google.common.collect.ImmutableList;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExperimentalApi;
import graphql.PublicApi;
import graphql.execution.Async;
import graphql.execution.DataFetcherResult;
import graphql.execution.ExecutionContext;
import graphql.execution.FieldValueInfo;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationReactiveResultsParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.language.Document;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.validation.ValidationError;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullUnmarked;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static graphql.Assert.assertNotNull;

/**
 * This allows you to chain together a number of {@link graphql.execution.instrumentation.Instrumentation} implementations
 * and run them in sequence.  The list order of instrumentation objects is always guaranteed to be followed and
 * the {@link graphql.execution.instrumentation.InstrumentationState} objects they create will be passed back to the originating
 * implementation.
 *
 * @see graphql.execution.instrumentation.Instrumentation
 */
@PublicApi
@NullMarked
public class ChainedInstrumentation implements Instrumentation {

    // This class is inspired from https://github.com/leangen/graphql-spqr/blob/master/src/main/java/io/leangen/graphql/GraphQLRuntime.java#L80

    protected final ImmutableList<Instrumentation> instrumentations;

    public ChainedInstrumentation(List<Instrumentation> instrumentations) {
        this.instrumentations = ImmutableList.copyOf(assertNotNull(instrumentations));
    }

    public ChainedInstrumentation(Instrumentation... instrumentations) {
        this(Arrays.asList(instrumentations));
    }

    /**
     * @return the list of instrumentations in play
     */
    public List<Instrumentation> getInstrumentations() {
        return instrumentations;
    }

    private <T> InstrumentationContext<T> chainedCtx(InstrumentationState state, BiFunction<Instrumentation, InstrumentationState, InstrumentationContext<T>> mapper) {
        // if we have zero or 1 instrumentations (and 1 is the most common), then we can avoid an object allocation
        // of the ChainedInstrumentationContext since it won't be needed
        if (instrumentations.isEmpty()) {
            return SimpleInstrumentationContext.noOp();
        }
        ChainedInstrumentationState chainedInstrumentationState = (ChainedInstrumentationState) state;
        if (instrumentations.size() == 1) {
            return mapper.apply(instrumentations.get(0), chainedInstrumentationState.getState(0));
        }
        return new ChainedInstrumentationContext<>(chainedMapAndDropNulls(chainedInstrumentationState, mapper));
    }

    private <T> T chainedInstrument(InstrumentationState state, T input, ChainedInstrumentationFunction<Instrumentation, InstrumentationState, T, T> mapper) {
        ChainedInstrumentationState chainedInstrumentationState = (ChainedInstrumentationState) state;
        for (int i = 0; i < instrumentations.size(); i++) {
            Instrumentation instrumentation = instrumentations.get(i);
            InstrumentationState specificState = chainedInstrumentationState.getState(i);
            input = mapper.apply(instrumentation, specificState, input);
        }
        return input;
    }

    protected <T> ImmutableList<T> chainedMapAndDropNulls(InstrumentationState state, BiFunction<Instrumentation, InstrumentationState, T> mapper) {
        ChainedInstrumentationState chainedInstrumentationState = (ChainedInstrumentationState) state;
        ImmutableList.Builder<T> result = ImmutableList.builderWithExpectedSize(instrumentations.size());
        for (int i = 0; i < instrumentations.size(); i++) {
            Instrumentation instrumentation = instrumentations.get(i);
            InstrumentationState specificState = chainedInstrumentationState.getState(i);
            T value = mapper.apply(instrumentation, specificState);
            if (value != null) {
                result.add(value);
            }
        }
        return result.build();
    }

    protected void chainedConsume(InstrumentationState state, BiConsumer<Instrumentation, InstrumentationState> stateConsumer) {
        ChainedInstrumentationState chainedInstrumentationState = (ChainedInstrumentationState) state;
        for (int i = 0; i < instrumentations.size(); i++) {
            Instrumentation instrumentation = instrumentations.get(i);
            InstrumentationState specificState = chainedInstrumentationState.getState(i);
            stateConsumer.accept(instrumentation, specificState);
        }
    }

    @Override
    public @NonNull CompletableFuture<InstrumentationState> createStateAsync(InstrumentationCreateStateParameters parameters) {
        return ChainedInstrumentationState.combineAll(instrumentations, parameters);
    }

    @Override
    public @Nullable InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return chainedCtx(state, (instrumentation, specificState) -> instrumentation.beginExecution(parameters, specificState));
    }


    @Override
    public @Nullable InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return chainedCtx(state, (instrumentation, specificState) -> instrumentation.beginParse(parameters, specificState));
    }


    @Override
    public @Nullable InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters, InstrumentationState state) {
        return chainedCtx(state, (instrumentation, specificState) -> instrumentation.beginValidation(parameters, specificState));
    }

    @Override
    public @Nullable InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
        return chainedCtx(state, (instrumentation, specificState) -> instrumentation.beginExecuteOperation(parameters, specificState));
    }

    @Override
    public @Nullable InstrumentationContext<Void> beginReactiveResults(InstrumentationReactiveResultsParameters parameters, InstrumentationState state) {
        return chainedCtx(state, (instrumentation, specificState) -> instrumentation.beginReactiveResults(parameters, specificState));
    }

    @Override
    public @Nullable ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {
        if (instrumentations.isEmpty()) {
            return ExecutionStrategyInstrumentationContext.NOOP;
        }
        BiFunction<Instrumentation, InstrumentationState, ExecutionStrategyInstrumentationContext> mapper = (instrumentation, specificState) -> instrumentation.beginExecutionStrategy(parameters, specificState);
        ChainedInstrumentationState chainedInstrumentationState = (ChainedInstrumentationState) state;
        if (instrumentations.size() == 1) {
            return mapper.apply(instrumentations.get(0), chainedInstrumentationState.getState(0));
        }
        return new ChainedExecutionStrategyInstrumentationContext(chainedMapAndDropNulls(chainedInstrumentationState, mapper));
    }

    @Override
    public @Nullable ExecuteObjectInstrumentationContext beginExecuteObject(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {
        if (instrumentations.isEmpty()) {
            return ExecuteObjectInstrumentationContext.NOOP;
        }
        BiFunction<Instrumentation, InstrumentationState, ExecuteObjectInstrumentationContext> mapper = (instrumentation, specificState) -> instrumentation.beginExecuteObject(parameters, specificState);
        ChainedInstrumentationState chainedInstrumentationState = (ChainedInstrumentationState) state;
        if (instrumentations.size() == 1) {
            return mapper.apply(instrumentations.get(0), chainedInstrumentationState.getState(0));
        }
        return new ChainedExecuteObjectInstrumentationContext(chainedMapAndDropNulls(chainedInstrumentationState, mapper));
    }

    @ExperimentalApi
    @Override
    public @Nullable InstrumentationContext<Object> beginDeferredField(InstrumentationFieldParameters parameters, InstrumentationState state) {
        return chainedCtx(state, (instrumentation, specificState) -> instrumentation.beginDeferredField(parameters, specificState));
    }

    @Override
    public @Nullable InstrumentationContext<ExecutionResult> beginSubscribedFieldEvent(InstrumentationFieldParameters parameters, InstrumentationState state) {
        return chainedCtx(state, (instrumentation, specificState) -> instrumentation.beginSubscribedFieldEvent(parameters, specificState));
    }

    @Override
    public @Nullable InstrumentationContext<Object> beginFieldExecution(InstrumentationFieldParameters parameters, InstrumentationState state) {
        return chainedCtx(state, (instrumentation, specificState) -> instrumentation.beginFieldExecution(parameters, specificState));
    }

    @SuppressWarnings("deprecation")
    @Override
    public @Nullable InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        return chainedCtx(state, (instrumentation, specificState) -> instrumentation.beginFieldFetch(parameters, specificState));
    }

    @Override
    public @Nullable FieldFetchingInstrumentationContext beginFieldFetching(InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        if (instrumentations.isEmpty()) {
            return FieldFetchingInstrumentationContext.NOOP;
        }
        BiFunction<Instrumentation, InstrumentationState, FieldFetchingInstrumentationContext> mapper = (instrumentation, specificState) -> instrumentation.beginFieldFetching(parameters, specificState);
        ChainedInstrumentationState chainedInstrumentationState = (ChainedInstrumentationState) state;
        if (instrumentations.size() == 1) {
            return mapper.apply(instrumentations.get(0), chainedInstrumentationState.getState(0));
        }
        ImmutableList<FieldFetchingInstrumentationContext> objects = chainedMapAndDropNulls(chainedInstrumentationState, mapper);
        return new ChainedFieldFetchingInstrumentationContext(objects);
    }

    @Override
    public @Nullable InstrumentationContext<Object> beginFieldCompletion(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        return chainedCtx(state, (instrumentation, specificState) -> instrumentation.beginFieldCompletion(parameters, specificState));
    }


    @Override
    public @Nullable InstrumentationContext<Object> beginFieldListCompletion(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        return chainedCtx(state, (instrumentation, specificState) -> instrumentation.beginFieldListCompletion(parameters, specificState));
    }

    @NonNull
    @Override
    public ExecutionInput instrumentExecutionInput(ExecutionInput executionInput, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return chainedInstrument(state, executionInput, (instrumentation, specificState, accumulator) -> instrumentation.instrumentExecutionInput(accumulator, parameters, specificState));
    }

    @NonNull
    @Override
    public DocumentAndVariables instrumentDocumentAndVariables(DocumentAndVariables documentAndVariables, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return chainedInstrument(state, documentAndVariables, (instrumentation, specificState, accumulator) ->
                instrumentation.instrumentDocumentAndVariables(accumulator, parameters, specificState));
    }

    @NonNull
    @Override
    public GraphQLSchema instrumentSchema(GraphQLSchema schema, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return chainedInstrument(state, schema, (instrumentation, specificState, accumulator) ->
                instrumentation.instrumentSchema(accumulator, parameters, specificState));
    }

    @NonNull
    @Override
    public ExecutionContext instrumentExecutionContext(ExecutionContext executionContext, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return chainedInstrument(state, executionContext, (instrumentation, specificState, accumulator) ->
                instrumentation.instrumentExecutionContext(accumulator, parameters, specificState));
    }

    @NonNull
    @Override
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        return chainedInstrument(state, dataFetcher, (Instrumentation instrumentation, InstrumentationState specificState, DataFetcher<?> accumulator) ->
                instrumentation.instrumentDataFetcher(accumulator, parameters, specificState));
    }

    @NonNull
    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        ImmutableList<Map.Entry<Instrumentation, InstrumentationState>> entries = chainedMapAndDropNulls(state, AbstractMap.SimpleEntry::new);
        CompletableFuture<List<ExecutionResult>> resultsFuture = Async.eachSequentially(entries, (entry, prevResults) -> {
            Instrumentation instrumentation = entry.getKey();
            InstrumentationState specificState = entry.getValue();
            ExecutionResult lastResult = !prevResults.isEmpty() ? prevResults.get(prevResults.size() - 1) : executionResult;
            return instrumentation.instrumentExecutionResult(lastResult, parameters, specificState);
        });
        return resultsFuture.thenApply((results) -> results.isEmpty() ? executionResult : results.get(results.size() - 1));
    }

    @NullUnmarked
    static class ChainedInstrumentationState implements InstrumentationState {
        private final List<InstrumentationState> instrumentationStates;

        private ChainedInstrumentationState(List<InstrumentationState> instrumentationStates) {
            this.instrumentationStates = instrumentationStates;
        }

        private InstrumentationState getState(int index) {
            return instrumentationStates.get(index);
        }

        private static CompletableFuture<InstrumentationState> combineAll(List<Instrumentation> instrumentations, InstrumentationCreateStateParameters parameters) {
            Async.CombinedBuilder<InstrumentationState> builder = Async.ofExpectedSize(instrumentations.size());
            for (Instrumentation instrumentation : instrumentations) {
                // state can be null including the CF so handle that
                CompletableFuture<InstrumentationState> stateCF = Async.orNullCompletedFuture(instrumentation.createStateAsync(parameters));
                builder.add(stateCF);
            }
            return builder.await().thenApply(ChainedInstrumentationState::new);
        }
    }

    @NullUnmarked
    private static class ChainedInstrumentationContext<T> implements InstrumentationContext<T> {

        private final ImmutableList<InstrumentationContext<T>> contexts;

        ChainedInstrumentationContext(ImmutableList<InstrumentationContext<T>> contexts) {
            this.contexts = contexts;
        }

        @Override
        public void onDispatched() {
            contexts.forEach(InstrumentationContext::onDispatched);
        }

        @Override
        public void onCompleted(T result, Throwable t) {
            contexts.forEach(context -> context.onCompleted(result, t));
        }
    }

    @NullUnmarked
    private static class ChainedExecutionStrategyInstrumentationContext implements ExecutionStrategyInstrumentationContext {

        private final ImmutableList<ExecutionStrategyInstrumentationContext> contexts;

        ChainedExecutionStrategyInstrumentationContext(ImmutableList<ExecutionStrategyInstrumentationContext> contexts) {
            this.contexts = contexts;
        }

        @Override
        public void onDispatched() {
            contexts.forEach(InstrumentationContext::onDispatched);
        }

        @Override
        public void onCompleted(ExecutionResult result, Throwable t) {
            contexts.forEach(context -> context.onCompleted(result, t));
        }

        @Override
        public void onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) {
            contexts.forEach(context -> context.onFieldValuesInfo(fieldValueInfoList));
        }

        @Override
        public void onFieldValuesException() {
            contexts.forEach(ExecutionStrategyInstrumentationContext::onFieldValuesException);
        }
    }

    @NullUnmarked
    private static class ChainedExecuteObjectInstrumentationContext implements ExecuteObjectInstrumentationContext {

        private final ImmutableList<ExecuteObjectInstrumentationContext> contexts;

        ChainedExecuteObjectInstrumentationContext(ImmutableList<ExecuteObjectInstrumentationContext> contexts) {
            this.contexts = contexts;
        }

        @Override
        public void onDispatched() {
            contexts.forEach(InstrumentationContext::onDispatched);
        }

        @Override
        public void onCompleted(Map<String, Object> result, Throwable t) {
            contexts.forEach(context -> context.onCompleted(result, t));
        }

        @Override
        public void onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) {
            contexts.forEach(context -> context.onFieldValuesInfo(fieldValueInfoList));
        }

        @Override
        public void onFieldValuesException() {
            contexts.forEach(ExecuteObjectInstrumentationContext::onFieldValuesException);
        }
    }

    @NullUnmarked
    private static class ChainedFieldFetchingInstrumentationContext implements FieldFetchingInstrumentationContext {

        private final ImmutableList<FieldFetchingInstrumentationContext> contexts;

        ChainedFieldFetchingInstrumentationContext(ImmutableList<FieldFetchingInstrumentationContext> contexts) {
            this.contexts = contexts;
        }

        @Override
        public void onDispatched() {
            contexts.forEach(FieldFetchingInstrumentationContext::onDispatched);
        }

        @Override
        public void onFetchedValue(Object fetchedValue) {
            contexts.forEach(context -> context.onFetchedValue(fetchedValue));
        }

        @Override
        public void onExceptionHandled(DataFetcherResult<Object> dataFetcherResult) {
            contexts.forEach(context -> context.onExceptionHandled(dataFetcherResult));
        }

        @Override
        public void onCompleted(Object result, Throwable t) {
            contexts.forEach(context -> context.onCompleted(result, t));
        }
    }

    @NullUnmarked
    private static class ChainedDeferredExecutionStrategyInstrumentationContext implements InstrumentationContext<Object> {


        private final List<InstrumentationContext<Object>> contexts;

        ChainedDeferredExecutionStrategyInstrumentationContext(List<InstrumentationContext<Object>> contexts) {
            this.contexts = Collections.unmodifiableList(contexts);
        }

        @Override
        public void onDispatched() {
            contexts.forEach(InstrumentationContext::onDispatched);
        }

        @Override
        public void onCompleted(Object result, Throwable t) {
            contexts.forEach(context -> context.onCompleted(result, t));
        }
    }

    @NullUnmarked
    @FunctionalInterface
    private interface ChainedInstrumentationFunction<I, S, V, R> {
        R apply(I instrumentation, S state, V value);
    }


}

