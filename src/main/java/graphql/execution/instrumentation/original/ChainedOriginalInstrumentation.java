package graphql.execution.instrumentation.original;

import com.google.common.collect.ImmutableList;
import graphql.Assert;
import graphql.ExecutionResult;
import graphql.PublicApi;
import graphql.execution.FieldValueInfo;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.original.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.original.parameters.InstrumentationFieldCompleteParameters;
import graphql.execution.instrumentation.original.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.original.parameters.InstrumentationFieldParameters;
import graphql.schema.DataFetcher;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import static graphql.collect.ImmutableKit.mapAndDropNulls;

/**
 * This allows you to chain together a number of {@link Instrumentation} implementations
 * and run them in sequence.  The list order of instrumentation objects is always guaranteed to be followed and
 * the {@link InstrumentationState} objects they create will be passed back to the originating
 * implementation.
 *
 * @see Instrumentation
 */
@SuppressWarnings("deprecation")
@PublicApi
public class ChainedOriginalInstrumentation extends ChainedInstrumentation implements OriginalInstrumentation {

    protected final ImmutableList<OriginalInstrumentation> originalInstrumentationsOnly;

    public ChainedOriginalInstrumentation(List<Instrumentation> instrumentations) {
        super(instrumentations);
        originalInstrumentationsOnly = onlyOriginals(instrumentations);
    }

    public ChainedOriginalInstrumentation(Instrumentation... instrumentations) {
        this(Arrays.asList(instrumentations));
    }

    private ImmutableList<OriginalInstrumentation> onlyOriginals(List<Instrumentation> instrumentations) {
        List<OriginalInstrumentation> collect = instrumentations.stream()
                .filter(i -> i instanceof OriginalInstrumentation)
                .map(OriginalInstrumentation.class::cast)
                .collect(Collectors.toList());
        return ImmutableList.copyOf(collect);
    }


    @Override
    @NotNull
    public ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginExecutionStrategy" + " was called");
    }

    @Override
    public ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {
        if (originalInstrumentationsOnly.isEmpty()) {
            return ExecutionStrategyInstrumentationContext.NOOP;
        }
        Function<OriginalInstrumentation, ExecutionStrategyInstrumentationContext> mapper = instrumentation -> {
            InstrumentationState specificState = getSpecificState(instrumentation, state);
            return instrumentation.beginExecutionStrategy(parameters, specificState);
        };
        if (originalInstrumentationsOnly.size() == 1) {
            return mapper.apply(originalInstrumentationsOnly.get(0));
        }
        return new ChainedExecutionStrategyInstrumentationContext(mapAndDropNulls(originalInstrumentationsOnly, mapper));
    }

    @Override
    @NotNull
    public InstrumentationContext<ExecutionResult> beginSubscribedFieldEvent(InstrumentationFieldParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginSubscribedFieldEvent" + " was called");
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginSubscribedFieldEvent(InstrumentationFieldParameters parameters, InstrumentationState state) {
        return chainedCtx(originalInstrumentationsOnly, instrumentation -> {
            InstrumentationState specificState = getSpecificState(instrumentation, state);
            return instrumentation.beginSubscribedFieldEvent(parameters, specificState);
        });
    }

    @Override
    @NotNull
    public InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginField" + " was called");
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters, InstrumentationState state) {
        return chainedCtx(originalInstrumentationsOnly, instrumentation -> {
            InstrumentationState specificState = getSpecificState(instrumentation, state);
            return instrumentation.beginField(parameters, specificState);
        });
    }

    @Override
    @NotNull
    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginFieldFetch" + " was called");
    }

    @Override
    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        return chainedCtx(originalInstrumentationsOnly, instrumentation -> {
            InstrumentationState specificState = getSpecificState(instrumentation, state);
            return instrumentation.beginFieldFetch(parameters, specificState);
        });
    }


    @Override
    @NotNull
    public InstrumentationContext<ExecutionResult> beginFieldComplete(InstrumentationFieldCompleteParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginFieldComplete" + " was called");
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginFieldComplete(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        return chainedCtx(originalInstrumentationsOnly, instrumentation -> {
            InstrumentationState specificState = getSpecificState(instrumentation, state);
            return instrumentation.beginFieldComplete(parameters, specificState);
        });
    }

    @Override
    @NotNull
    public InstrumentationContext<ExecutionResult> beginFieldListComplete(InstrumentationFieldCompleteParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginFieldListComplete" + " was called");
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginFieldListComplete(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        return chainedCtx(originalInstrumentationsOnly, instrumentation -> {
            InstrumentationState specificState = getSpecificState(instrumentation, state);
            return instrumentation.beginFieldListComplete(parameters, specificState);
        });
    }


    @Override
    @NotNull
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "instrumentDataFetcher" + " was called");
    }

    @NotNull
    @Override
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        if (originalInstrumentationsOnly.isEmpty()) {
            return dataFetcher;
        }
        for (OriginalInstrumentation instrumentation : originalInstrumentationsOnly) {
            InstrumentationState specificState = getSpecificState(instrumentation, state);
            dataFetcher = instrumentation.instrumentDataFetcher(dataFetcher, parameters, specificState);
        }
        return dataFetcher;
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

        @Override
        public void onFieldValuesException() {
            contexts.forEach(ExecutionStrategyInstrumentationContext::onFieldValuesException);
        }
    }

}

