package graphql.execution.instrumentation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import graphql.Assert;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.PublicApi;
import graphql.execution.Async;
import graphql.execution.ExecutionContext;
import graphql.execution.FieldValueInfo;
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
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static graphql.Assert.assertNotNull;
import static graphql.collect.ImmutableKit.mapAndDropNulls;

/**
 * This allows you to chain together a number of {@link graphql.execution.instrumentation.Instrumentation} implementations
 * and run them in sequence.  The list order of instrumentation objects is always guaranteed to be followed and
 * the {@link graphql.execution.instrumentation.InstrumentationState} objects they create will be passed back to the originating
 * implementation.
 *
 * @see graphql.execution.instrumentation.Instrumentation
 */
@PublicApi
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

    protected InstrumentationState getSpecificState(Instrumentation instrumentation, InstrumentationState parametersInstrumentationState) {
        ChainedInstrumentationState chainedInstrumentationState = (ChainedInstrumentationState) parametersInstrumentationState;
        return chainedInstrumentationState.getState(instrumentation);
    }

    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return new ChainedInstrumentationState(instrumentations, parameters);
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters) {
        // these assert methods have been left in so that we truly never call these methods, either in production nor in tests
        // later when the deprecated methods are removed, this will disappear.
        return Assert.assertShouldNeverHappen("The deprecated " + "beginExecution" + " was called");
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return new ChainedInstrumentationContext<>(mapAndDropNulls(instrumentations, instrumentation -> {
            InstrumentationState specificState = getSpecificState(instrumentation, state);
            return instrumentation.beginExecution(parameters, specificState);
        }));
    }

    @Override
    public InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginParse" + " was called");
    }

    @Override
    public InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return new ChainedInstrumentationContext<>(mapAndDropNulls(instrumentations, instrumentation -> {
            InstrumentationState specificState = getSpecificState(instrumentation, state);
            return instrumentation.beginParse(parameters, specificState);
        }));
    }

    @Override
    public InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginValidation" + " was called");
    }

    @Override
    public InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters, InstrumentationState state) {
        return new ChainedInstrumentationContext<>(mapAndDropNulls(instrumentations, instrumentation -> {
            InstrumentationState specificState = getSpecificState(instrumentation, state);
            return instrumentation.beginValidation(parameters, specificState);
        }));
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginExecuteOperation" + " was called");
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
        return new ChainedInstrumentationContext<>(mapAndDropNulls(instrumentations, instrumentation -> {
            InstrumentationState specificState = getSpecificState(instrumentation, state);
            return instrumentation.beginExecuteOperation(parameters, specificState);
        }));

    }

    @Override
    public ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginExecutionStrategy" + " was called");
    }

    @Override
    public ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {
        return new ChainedExecutionStrategyInstrumentationContext(mapAndDropNulls(instrumentations, instrumentation -> {
            InstrumentationState specificState = getSpecificState(instrumentation, state);
            return instrumentation.beginExecutionStrategy(parameters, specificState);
        }));
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginSubscribedFieldEvent(InstrumentationFieldParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginSubscribedFieldEvent" + " was called");
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginSubscribedFieldEvent(InstrumentationFieldParameters parameters, InstrumentationState state) {
        return new ChainedInstrumentationContext<>(mapAndDropNulls(instrumentations, instrumentation -> {
            InstrumentationState specificState = getSpecificState(instrumentation, state);
            return instrumentation.beginSubscribedFieldEvent(parameters, specificState);
        }));
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginField" + " was called");
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters, InstrumentationState state) {
        return new ChainedInstrumentationContext<>(mapAndDropNulls(instrumentations, instrumentation -> {
            InstrumentationState specificState = getSpecificState(instrumentation, state);
            return instrumentation.beginField(parameters, specificState);
        }));
    }

    @Override
    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginFieldFetch" + " was called");
    }

    @Override
    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        return new ChainedInstrumentationContext<>(mapAndDropNulls(instrumentations, instrumentation -> {
            InstrumentationState specificState = getSpecificState(instrumentation, state);
            return instrumentation.beginFieldFetch(parameters, specificState);
        }));
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginFieldComplete(InstrumentationFieldCompleteParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginFieldComplete" + " was called");
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginFieldComplete(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        return new ChainedInstrumentationContext<>(mapAndDropNulls(instrumentations, instrumentation -> {
            InstrumentationState specificState = getSpecificState(instrumentation, state);
            return instrumentation.beginFieldComplete(parameters, specificState);
        }));
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginFieldListComplete(InstrumentationFieldCompleteParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginFieldListComplete" + " was called");
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginFieldListComplete(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        return new ChainedInstrumentationContext<>(mapAndDropNulls(instrumentations, it -> {
            InstrumentationState specificState = getSpecificState(it, state);
            return it.beginFieldListComplete(parameters, specificState);
        }));
    }

    @Override
    public ExecutionInput instrumentExecutionInput(ExecutionInput executionInput, InstrumentationExecutionParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "instrumentExecutionInput" + " was called");
    }

    @NotNull
    @Override
    public ExecutionInput instrumentExecutionInput(ExecutionInput executionInput, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        for (Instrumentation instrumentation : instrumentations) {
            InstrumentationState specificState = getSpecificState(instrumentation, state);
            executionInput = instrumentation.instrumentExecutionInput(executionInput, parameters, specificState);
        }
        return executionInput;
    }

    @Override
    public DocumentAndVariables instrumentDocumentAndVariables(DocumentAndVariables documentAndVariables, InstrumentationExecutionParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "instrumentDocumentAndVariables" + " was called");
    }

    @NotNull
    @Override
    public DocumentAndVariables instrumentDocumentAndVariables(DocumentAndVariables documentAndVariables, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        for (Instrumentation instrumentation : instrumentations) {
            InstrumentationState specificState = getSpecificState(instrumentation, state);
            documentAndVariables = instrumentation.instrumentDocumentAndVariables(documentAndVariables, parameters, specificState);
        }
        return documentAndVariables;
    }

    @Override
    public GraphQLSchema instrumentSchema(GraphQLSchema schema, InstrumentationExecutionParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "instrumentSchema" + " was called");
    }

    @NotNull
    @Override
    public GraphQLSchema instrumentSchema(GraphQLSchema schema, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        for (Instrumentation instrumentation : instrumentations) {
            InstrumentationState specificState = getSpecificState(instrumentation, state);
            schema = instrumentation.instrumentSchema(schema, parameters, specificState);
        }
        return schema;
    }

    @Override
    public ExecutionContext instrumentExecutionContext(ExecutionContext executionContext, InstrumentationExecutionParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "instrumentExecutionContext" + " was called");
    }

    @NotNull
    @Override
    public ExecutionContext instrumentExecutionContext(ExecutionContext executionContext, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        for (Instrumentation instrumentation : instrumentations) {
            InstrumentationState specificState = getSpecificState(instrumentation, state);
            executionContext = instrumentation.instrumentExecutionContext(executionContext, parameters, specificState);
        }
        return executionContext;
    }

    @Override
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "instrumentDataFetcher" + " was called");
    }

    @NotNull
    @Override
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        for (Instrumentation instrumentation : instrumentations) {
            InstrumentationState specificState = getSpecificState(instrumentation, state);
            dataFetcher = instrumentation.instrumentDataFetcher(dataFetcher, parameters, specificState);
        }
        return dataFetcher;
    }

    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "instrumentExecutionResult" + " was called");
    }

    @NotNull
    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        CompletableFuture<List<ExecutionResult>> resultsFuture = Async.eachSequentially(instrumentations, (instrumentation, index, prevResults) -> {
            InstrumentationState specificState = getSpecificState(instrumentation, state);
            ExecutionResult lastResult = prevResults.size() > 0 ? prevResults.get(prevResults.size() - 1) : executionResult;
            return instrumentation.instrumentExecutionResult(lastResult, parameters, specificState);
        });
        return resultsFuture.thenApply((results) -> results.isEmpty() ? executionResult : results.get(results.size() - 1));
    }

    static class ChainedInstrumentationState implements InstrumentationState {
        private final Map<Instrumentation, InstrumentationState> instrumentationStates;


        private ChainedInstrumentationState(List<Instrumentation> instrumentations, InstrumentationCreateStateParameters parameters) {
            instrumentationStates = Maps.newLinkedHashMapWithExpectedSize(instrumentations.size());
            instrumentations.forEach(i -> instrumentationStates.put(i, i.createState(parameters)));
        }

        private InstrumentationState getState(Instrumentation instrumentation) {
            return instrumentationStates.get(instrumentation);
        }

    }

    private static class ChainedInstrumentationContext<T> implements InstrumentationContext<T> {

        private final ImmutableList<InstrumentationContext<T>> contexts;

        ChainedInstrumentationContext(ImmutableList<InstrumentationContext<T>> contexts) {
            this.contexts = contexts;
        }

        @Override
        public void onDispatched(CompletableFuture<T> result) {
            contexts.forEach(context -> context.onDispatched(result));
        }

        @Override
        public void onCompleted(T result, Throwable t) {
            contexts.forEach(context -> context.onCompleted(result, t));
        }
    }

    private static class ChainedExecutionStrategyInstrumentationContext implements ExecutionStrategyInstrumentationContext {

        private final ImmutableList<ExecutionStrategyInstrumentationContext> contexts;

        ChainedExecutionStrategyInstrumentationContext(ImmutableList<ExecutionStrategyInstrumentationContext> contexts) {
            this.contexts = contexts;
        }

        @Override
        public void onDispatched(CompletableFuture<ExecutionResult> result) {
            contexts.forEach(context -> context.onDispatched(result));
        }

        @Override
        public void onCompleted(ExecutionResult result, Throwable t) {
            contexts.forEach(context -> context.onCompleted(result, t));
        }

        @Override
        public void onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) {
            contexts.forEach(context -> context.onFieldValuesInfo(fieldValueInfoList));
        }

    }

}

