package graphql.execution.instrumentation;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.PublicApi;
import graphql.execution.Async;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.parameters.InstrumentationCreatePreExecutionStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationDataFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionContextParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionResultParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.language.Document;
import graphql.language.Field;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.validation.ValidationError;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static graphql.Assert.assertNotNull;
import static java.util.stream.Collectors.toList;

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

    private final List<Instrumentation> instrumentations;

    public ChainedInstrumentation(List<Instrumentation> instrumentations) {
        this.instrumentations = Collections.unmodifiableList(assertNotNull(instrumentations));
    }

    private InstrumentationState getState(Instrumentation instrumentation, InstrumentationState parametersInstrumentationState) {
        ChainedInstrumentationState chainedInstrumentationState = (ChainedInstrumentationState) parametersInstrumentationState;
        return chainedInstrumentationState.getState(instrumentation);
    }

    private InstrumentationPreExecutionState getPreExecutionState(Instrumentation instrumentation, InstrumentationPreExecutionState preExecutionState) {
        ChainedInstrumentationPreExecutionState chainedInstrumentationState = (ChainedInstrumentationPreExecutionState) preExecutionState;
        return chainedInstrumentationState.getPreExecutionState(instrumentation);
    }

    @Override
    public InstrumentationPreExecutionState createPreExecutionState(InstrumentationCreatePreExecutionStateParameters parameters) {
        return new ChainedInstrumentationPreExecutionState(instrumentations, parameters);
    }

    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return new ChainedInstrumentationState(instrumentations, parameters);
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecution(final InstrumentationExecutionParameters parameters) {
        return new ChainedInstrumentationContext<>(instrumentations.stream()
                .map(instrumentation -> {
                    InstrumentationPreExecutionState state = getPreExecutionState(instrumentation, parameters.getPreExecutionState());
                    return instrumentation.beginExecution(parameters.withNewState(state));
                })
                .collect(toList()));
    }

    @Override
    public InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters) {
        return new ChainedInstrumentationContext<>(instrumentations.stream()
                .map(instrumentation -> {
                    InstrumentationPreExecutionState state = getPreExecutionState(instrumentation, parameters.getPreExecutionState());
                    return instrumentation.beginParse(parameters.withNewState(state));
                })
                .collect(toList()));
    }

    @Override
    public InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
        return new ChainedInstrumentationContext<>(instrumentations.stream()
                .map(instrumentation -> {
                    InstrumentationPreExecutionState state = getPreExecutionState(instrumentation, parameters.getPreExecutionState());
                    return instrumentation.beginValidation(parameters.withNewState(state));
                })
                .collect(toList()));
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginDataFetch(InstrumentationDataFetchParameters parameters) {
        return new ChainedInstrumentationContext<>(instrumentations.stream()
                .map(instrumentation -> {
                    InstrumentationState state = getState(instrumentation, parameters.getInstrumentationState());
                    return instrumentation.beginDataFetch(parameters.withNewState(state));
                })
                .collect(toList()));
    }

    @Override
    public InstrumentationContext<CompletableFuture<ExecutionResult>> beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        return new ChainedInstrumentationContext<>(instrumentations.stream()
                .map(instrumentation -> {
                    InstrumentationState state = getState(instrumentation, parameters.getInstrumentationState());
                    return instrumentation.beginExecutionStrategy(parameters.withNewState(state));
                })
                .collect(toList()));
    }

    @Override
    public InstrumentationContext<CompletableFuture<ExecutionResult>> beginDataFetchDispatch(InstrumentationDataFetchParameters parameters) {
        return new ChainedInstrumentationContext<>(instrumentations.stream()
                .map(instrumentation -> {
                    InstrumentationState state = getState(instrumentation, parameters.getInstrumentationState());
                    return instrumentation.beginDataFetchDispatch(parameters.withNewState(state));
                })
                .collect(toList()));
    }

    @Override
    public InstrumentationContext<Map<String, List<Field>>> beginFields(InstrumentationExecutionStrategyParameters parameters) {
        return new ChainedInstrumentationContext<>(instrumentations.stream()
                .map(instrumentation -> {
                    InstrumentationState state = getState(instrumentation, parameters.getInstrumentationState());
                    return instrumentation.beginFields(parameters.withNewState(state));
                })
                .collect(toList()));
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters) {
        return new ChainedInstrumentationContext<>(instrumentations.stream()
                .map(instrumentation -> {
                    InstrumentationState state = getState(instrumentation, parameters.getInstrumentationState());
                    return instrumentation.beginField(parameters.withNewState(state));
                })
                .collect(toList()));
    }

    @Override
    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        return new ChainedInstrumentationContext<>(instrumentations.stream()
                .map(instrumentation -> {
                    InstrumentationState state = getState(instrumentation, parameters.getInstrumentationState());
                    return instrumentation.beginFieldFetch(parameters.withNewState(state));
                })
                .collect(toList()));
    }

    @Override
    public InstrumentationContext<CompletableFuture<ExecutionResult>> beginCompleteField(InstrumentationFieldCompleteParameters parameters) {
        return new ChainedInstrumentationContext<>(instrumentations.stream()
                .map(instrumentation -> {
                    InstrumentationState state = getState(instrumentation, parameters.getInstrumentationState());
                    return instrumentation.beginCompleteField(parameters.withNewState(state));
                })
                .collect(toList()));
    }

    @Override
    public InstrumentationContext<CompletableFuture<ExecutionResult>> beginCompleteFieldList(InstrumentationFieldCompleteParameters parameters) {
        return new ChainedInstrumentationContext<>(instrumentations.stream()
                .map(instrumentation -> {
                    InstrumentationState state = getState(instrumentation, parameters.getInstrumentationState());
                    return instrumentation.beginCompleteFieldList(parameters.withNewState(state));
                })
                .collect(toList()));
    }

    @Override
    public ExecutionInput instrumentExecutionInput(ExecutionInput executionInput, InstrumentationExecutionParameters parameters) {
        for (Instrumentation instrumentation : instrumentations) {
            InstrumentationPreExecutionState state = getPreExecutionState(instrumentation, parameters.getPreExecutionState());
            executionInput = instrumentation.instrumentExecutionInput(executionInput, parameters.withNewState(state));
        }
        return executionInput;
    }

    @Override
    public GraphQLSchema instrumentSchema(GraphQLSchema schema, InstrumentationExecutionParameters parameters) {
        for (Instrumentation instrumentation : instrumentations) {
            InstrumentationPreExecutionState state = getPreExecutionState(instrumentation, parameters.getPreExecutionState());
            schema = instrumentation.instrumentSchema(schema, parameters.withNewState(state));
        }
        return schema;
    }

    @Override
    public ExecutionContext instrumentExecutionContext(ExecutionContext executionContext, InstrumentationExecutionContextParameters parameters) {
        for (Instrumentation instrumentation : instrumentations) {
            InstrumentationState state = getState(instrumentation, parameters.getInstrumentationState());
            executionContext = instrumentation.instrumentExecutionContext(executionContext, parameters.withNewState(state));
        }
        return executionContext;
    }

    @Override
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
        for (Instrumentation instrumentation : instrumentations) {
            InstrumentationState state = getState(instrumentation, parameters.getInstrumentationState());
            dataFetcher = instrumentation.instrumentDataFetcher(dataFetcher, parameters.withNewState(state));
        }
        return dataFetcher;
    }


    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionResultParameters parameters) {
        CompletableFuture<List<ExecutionResult>> resultsFuture = Async.eachSequentially(instrumentations, (instrumentation, index, prevResults) -> {
            InstrumentationState state = getState(instrumentation, parameters.getInstrumentationState());
            ExecutionResult lastResult = prevResults.size() > 0 ? prevResults.get(prevResults.size() - 1) : executionResult;
            return instrumentation.instrumentExecutionResult(lastResult, parameters.withNewState(state));
        });
        return resultsFuture.thenApply((results) -> results.isEmpty() ? executionResult : results.get(results.size() - 1));
    }

    @Override
    public CompletableFuture<ExecutionResult> instrumentFinalExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
        CompletableFuture<List<ExecutionResult>> resultsFuture = Async.eachSequentially(instrumentations, (instrumentation, index, prevResults) -> {
            InstrumentationPreExecutionState state = getPreExecutionState(instrumentation, parameters.getPreExecutionState());
            ExecutionResult lastResult = prevResults.size() > 0 ? prevResults.get(prevResults.size() - 1) : executionResult;
            return instrumentation.instrumentFinalExecutionResult(lastResult, parameters.withNewState(state));
        });
        return resultsFuture.thenApply((results) -> results.isEmpty() ? executionResult : results.get(results.size() - 1));
    }

    private static class ChainedInstrumentationState implements InstrumentationState {
        private final Map<Instrumentation, InstrumentationState> instrumentationStates;

        private ChainedInstrumentationState(List<Instrumentation> instrumentations, InstrumentationCreateStateParameters parameters) {
            instrumentationStates = new LinkedHashMap<>(instrumentations.size());
            instrumentations.forEach(i -> instrumentationStates.put(i, i.createState(parameters)));
        }

        private InstrumentationState getState(Instrumentation instrumentation) {
            return instrumentationStates.get(instrumentation);
        }
    }

    private static class ChainedInstrumentationPreExecutionState implements InstrumentationPreExecutionState {
        private final Map<Instrumentation, InstrumentationPreExecutionState> instrumentationStates;

        private ChainedInstrumentationPreExecutionState(List<Instrumentation> instrumentations, InstrumentationCreatePreExecutionStateParameters parameters) {
            instrumentationStates = new LinkedHashMap<>(instrumentations.size());
            instrumentations.forEach(i -> instrumentationStates.put(i, i.createPreExecutionState(parameters)));
        }

        private InstrumentationPreExecutionState getPreExecutionState(Instrumentation instrumentation) {
            return instrumentationStates.get(instrumentation);
        }

    }

    private static class ChainedInstrumentationContext<T> implements InstrumentationContext<T> {

        private final List<InstrumentationContext<T>> contexts;

        ChainedInstrumentationContext(List<InstrumentationContext<T>> contexts) {
            this.contexts = Collections.unmodifiableList(contexts);
        }

        @Override
        public void onEnd(T result, Throwable t) {
            contexts.forEach(context -> context.onEnd(result, t));
        }
    }
}

