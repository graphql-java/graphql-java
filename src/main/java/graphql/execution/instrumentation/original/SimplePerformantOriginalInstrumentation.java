package graphql.execution.instrumentation.original;

import graphql.ExecutionResult;
import graphql.PublicApi;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.original.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.original.parameters.InstrumentationFieldCompleteParameters;
import graphql.execution.instrumentation.original.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.original.parameters.InstrumentationFieldParameters;
import graphql.schema.DataFetcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static graphql.Assert.assertShouldNeverHappen;
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
public class SimplePerformantOriginalInstrumentation extends SimplePerformantInstrumentation implements OriginalInstrumentation {

    /**
     * A singleton instance of a {@link Instrumentation} that does nothing
     */
    public static final SimplePerformantOriginalInstrumentation INSTANCE = new SimplePerformantOriginalInstrumentation();


    @Override
    public @NotNull ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        return assertShouldNeverHappen("The deprecated " + "beginExecutionStrategy" + " was called");
    }

    @Override
    public @Nullable ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {
        return ExecutionStrategyInstrumentationContext.NOOP;
    }

    @Override
    public @NotNull InstrumentationContext<ExecutionResult> beginSubscribedFieldEvent(InstrumentationFieldParameters parameters) {
        return assertShouldNeverHappen("The deprecated " + "beginSubscribedFieldEvent" + " was called");
    }

    @Override
    public @Nullable InstrumentationContext<ExecutionResult> beginSubscribedFieldEvent(InstrumentationFieldParameters parameters, InstrumentationState state) {
        return noOp();
    }

    @Override
    public @NotNull InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters) {
        return assertShouldNeverHappen("The deprecated " + "beginField" + " was called");
    }

    @Override
    public @Nullable InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters, InstrumentationState state) {
        return noOp();
    }

    @Override
    public @NotNull InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        return assertShouldNeverHappen("The deprecated " + "beginFieldFetch" + " was called");
    }

    @Override
    public @Nullable InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        return noOp();
    }

    @Override
    public @NotNull InstrumentationContext<ExecutionResult> beginFieldComplete(InstrumentationFieldCompleteParameters parameters) {
        return assertShouldNeverHappen("The deprecated " + "beginFieldComplete" + " was called");
    }

    @Override
    public @Nullable InstrumentationContext<ExecutionResult> beginFieldComplete(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        return noOp();
    }

    @Override
    public @NotNull InstrumentationContext<ExecutionResult> beginFieldListComplete(InstrumentationFieldCompleteParameters parameters) {
        return assertShouldNeverHappen("The deprecated " + "beginFieldListComplete" + " was called");
    }

    @Override
    public @Nullable InstrumentationContext<ExecutionResult> beginFieldListComplete(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        return noOp();
    }




    @Override
    public @NotNull DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
        return assertShouldNeverHappen("The deprecated " + "instrumentDataFetcher" + " was called");
    }

    @Override
    public @NotNull DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        return dataFetcher;
    }

}
