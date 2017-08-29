package graphql.execution.instrumentation;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.parameters.InstrumentationDataFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.language.Document;
import graphql.schema.DataFetcher;
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

    @Override
    public InstrumentationState createState() {
        return new ChainedInstrumentationState(instrumentations);
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecution(final InstrumentationExecutionParameters parameters) {
        return new ChainedInstrumentationContext<>(instrumentations.stream()
                .map(instrumentation -> {
                    InstrumentationState state = getState(instrumentation, parameters.getInstrumentationState());
                    return instrumentation.beginExecution(parameters.withNewState(state));
                })
                .collect(toList()));
    }

    @Override
    public InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters) {
        return new ChainedInstrumentationContext<>(instrumentations.stream()
                .map(instrumentation -> {
                    InstrumentationState state = getState(instrumentation, parameters.getInstrumentationState());
                    return instrumentation.beginParse(parameters.withNewState(state));
                })
                .collect(toList()));
    }

    @Override
    public InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
        return new ChainedInstrumentationContext<>(instrumentations.stream()
                .map(instrumentation -> {
                    InstrumentationState state = getState(instrumentation, parameters.getInstrumentationState());
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
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
        for (Instrumentation instrumentation : instrumentations) {
            InstrumentationState state = getState(instrumentation, parameters.getInstrumentationState());
            dataFetcher = instrumentation.instrumentDataFetcher(dataFetcher, parameters.withNewState(state));
        }
        return dataFetcher;
    }

    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(CompletableFuture<ExecutionResult> executionResultFuture, InstrumentationExecutionParameters parameters) {
        for (Instrumentation instrumentation : instrumentations) {
            InstrumentationState state = getState(instrumentation, parameters.getInstrumentationState());
            executionResultFuture = instrumentation
                    .instrumentExecutionResult(executionResultFuture, parameters.withNewState(state));
        }
        return executionResultFuture;
    }

    private static class ChainedInstrumentationState implements InstrumentationState {
        private final Map<Instrumentation, InstrumentationState> instrumentationStates;


        private ChainedInstrumentationState(List<Instrumentation> instrumentations) {
            instrumentationStates = new LinkedHashMap<>(instrumentations.size());
            instrumentations.forEach(i -> instrumentationStates.put(i, i.createState()));
        }

        private InstrumentationState getState(Instrumentation instrumentation) {
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

