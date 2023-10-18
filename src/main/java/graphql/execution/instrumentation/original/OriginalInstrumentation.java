package graphql.execution.instrumentation.original;

import graphql.DeprecatedAt;
import graphql.ExecutionResult;
import graphql.PublicSpi;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.original.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.original.parameters.InstrumentationFieldCompleteParameters;
import graphql.execution.instrumentation.original.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.original.parameters.InstrumentationFieldParameters;
import graphql.schema.DataFetcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static graphql.execution.instrumentation.SimpleInstrumentationContext.noOp;

/**
 * Provides the capability to instrument the execution steps of the original engine.
 * <p>
 * For example you might want to track which fields are taking the most time to fetch from the backing database
 * or log what fields are being asked for.
 * <p>
 * Remember that graphql calls can cross threads so make sure you think about the thread safety of any instrumentation
 * code when you are writing it.
 * <p>
 * Each step gives back an {@link InstrumentationContext} object.  This has two callbacks on it,
 * one for the step is `dispatched` and one for when the step has `completed`.  This is done because many of the "steps" are asynchronous
 * operations such as fetching data and resolving it into objects.
 */
@PublicSpi
public interface OriginalInstrumentation extends Instrumentation {

    /**
     * This is called each time an {@link graphql.execution.ExecutionStrategy} is invoked, which may be multiple times
     * per query as the engine recursively descends down over the query.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link ExecutionStrategyInstrumentationContext} object that will be called back when the step ends
     *
     * @deprecated use {@link #beginExecutionStrategy(InstrumentationExecutionStrategyParameters, InstrumentationState)} instead
     */
    @Deprecated
    @DeprecatedAt("2022-07-26")
    @NotNull
    default ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        return ExecutionStrategyInstrumentationContext.NOOP;
    }

    /**
     * This is called each time an {@link graphql.execution.ExecutionStrategy} is invoked, which may be multiple times
     * per query as the engine recursively descends down over the query.
     *
     * @param parameters the parameters to this step
     * @param state      the state created during the call to {@link #createState(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link ExecutionStrategyInstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     */
    @Nullable
    default ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {
        return beginExecutionStrategy(parameters.withNewState(state));
    }


    /**
     * This is called each time a subscription field produces a new reactive stream event value and it needs to be mapped over via the graphql field subselection.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     *
     * @deprecated use {@link #beginSubscribedFieldEvent(InstrumentationFieldParameters, InstrumentationState)}  instead
     */
    @Deprecated
    @DeprecatedAt("2022-07-26")
    @NotNull
    default InstrumentationContext<ExecutionResult> beginSubscribedFieldEvent(InstrumentationFieldParameters parameters) {
        return noOp();
    }

    /**
     * This is called each time a subscription field produces a new reactive stream event value and it needs to be mapped over via the graphql field subselection.
     *
     * @param parameters the parameters to this step
     * @param state      the state created during the call to {@link #createState(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link InstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     */
    @Nullable
    default InstrumentationContext<ExecutionResult> beginSubscribedFieldEvent(InstrumentationFieldParameters parameters, InstrumentationState state) {
        return beginSubscribedFieldEvent(parameters.withNewState(state));
    }

    /**
     * This is called just before a field is resolved into a value.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     *
     * @deprecated use {@link #beginField(InstrumentationFieldParameters, InstrumentationState)}   instead
     */
    @Deprecated
    @DeprecatedAt("2022-07-26")
    @NotNull
    default InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters) {
        return noOp();
    }

    /**
     * This is called just before a field is resolved into a value.
     *
     * @param parameters the parameters to this step
     * @param state      the state created during the call to {@link #createState(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link InstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     */
    @Nullable
    default InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters, InstrumentationState state) {
        return beginField(parameters.withNewState(state));
    }

    /**
     * This is called just before a field {@link DataFetcher} is invoked.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     *
     * @deprecated use {@link #beginFieldFetch(InstrumentationFieldFetchParameters, InstrumentationState)} instead
     */
    @Deprecated
    @DeprecatedAt("2022-07-26")
    @NotNull
    default InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        return noOp();
    }

    /**
     * This is called just before a field {@link DataFetcher} is invoked.
     *
     * @param parameters the parameters to this step
     * @param state      the state created during the call to {@link #createState(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link InstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     */
    @Nullable
    default InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        return beginFieldFetch(parameters.withNewState(state));
    }


    /**
     * This is called just before the complete field is started.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     *
     * @deprecated use {@link #beginFieldComplete(InstrumentationFieldCompleteParameters, InstrumentationState)} instead
     */
    @Deprecated
    @DeprecatedAt("2022-07-26")
    @NotNull
    default InstrumentationContext<ExecutionResult> beginFieldComplete(InstrumentationFieldCompleteParameters parameters) {
        return noOp();
    }

    /**
     * This is called just before the complete field is started.
     *
     * @param parameters the parameters to this step
     * @param state      the state created during the call to {@link #createState(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link InstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     */
    @Nullable
    default InstrumentationContext<ExecutionResult> beginFieldComplete(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        return beginFieldComplete(parameters.withNewState(state));
    }

    /**
     * This is called just before the complete field list is started.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     *
     * @deprecated use {@link #beginFieldListComplete(InstrumentationFieldCompleteParameters, InstrumentationState)}  instead
     */
    @Deprecated
    @DeprecatedAt("2022-07-26")
    @NotNull
    default InstrumentationContext<ExecutionResult> beginFieldListComplete(InstrumentationFieldCompleteParameters parameters) {
        return noOp();
    }

    /**
     * This is called just before the complete field list is started.
     *
     * @param parameters the parameters to this step
     * @param state      the state created during the call to {@link #createState(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link InstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     */
    @Nullable
    default InstrumentationContext<ExecutionResult> beginFieldListComplete(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        return beginFieldListComplete(parameters.withNewState(state));
    }



    /**
     * This is called to instrument a {@link DataFetcher} just before it is used to fetch a field, allowing you
     * to adjust what information is passed back or record information about specific data fetches.  Note
     * the same data fetcher instance maybe presented to you many times and that data fetcher
     * implementations widely vary.
     *
     * @param dataFetcher the data fetcher about to be used
     * @param parameters  the parameters describing the field to be fetched
     *
     * @return a non null instrumented DataFetcher, the default is to return to the same object
     *
     * @deprecated use {@link #instrumentDataFetcher(DataFetcher, InstrumentationFieldFetchParameters, InstrumentationState)}  instead
     */
    @Deprecated
    @DeprecatedAt("2022-07-26")
    @NotNull
    default DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
        return dataFetcher;
    }

    /**
     * This is called to instrument a {@link DataFetcher} just before it is used to fetch a field, allowing you
     * to adjust what information is passed back or record information about specific data fetches.  Note
     * the same data fetcher instance maybe presented to you many times and that data fetcher
     * implementations widely vary.
     *
     * @param dataFetcher the data fetcher about to be used
     * @param parameters  the parameters describing the field to be fetched
     * @param state       the state created during the call to {@link #createState(InstrumentationCreateStateParameters)}
     *
     * @return a non null instrumented DataFetcher, the default is to return to the same object
     */
    @NotNull
    default DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        return instrumentDataFetcher(dataFetcher, parameters.withNewState(state));
    }

}
