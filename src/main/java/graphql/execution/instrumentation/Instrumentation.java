package graphql.execution.instrumentation;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExperimentalApi;
import graphql.PublicSpi;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static graphql.execution.instrumentation.SimpleInstrumentationContext.noOp;

/**
 * Provides the capability to instrument the execution steps of a GraphQL query.
 * <p>
 * For example you might want to track which fields are taking the most time to fetch from the backing database
 * or log what fields are being asked for.
 * <p>
 * Remember that graphql calls can cross threads so make sure you think about the thread safety of any instrumentation
 * code when you are writing it.
 * <p>
 * Each step gives back an {@link graphql.execution.instrumentation.InstrumentationContext} object.  This has two callbacks on it,
 * one for the step is `dispatched` and one for when the step has `completed`.  This is done because many of the "steps" are asynchronous
 * operations such as fetching data and resolving it into objects.
 */
@PublicSpi
public interface Instrumentation {
    /**
     * This will be called just before execution to create an object, in an asynchronous manner, that is given back to all instrumentation methods
     * to allow them to have per execution request state
     *
     * @param parameters the parameters to this step
     *
     * @return a state object that is passed to each method
     */
    @Nullable
    default CompletableFuture<InstrumentationState> createStateAsync(InstrumentationCreateStateParameters parameters) {
        InstrumentationState state = createState(parameters);
        return state == null ? null : CompletableFuture.completedFuture(state);
    }

    /**
     * This method is retained for backwards compatibility reasons so that previous {@link Instrumentation} implementations
     * continue to work.  The graphql-java code only called {@link #createStateAsync(InstrumentationCreateStateParameters)}
     * but the default implementation calls back to this method.
     *
     * @param parameters the parameters to this step
     *
     * @return a state object that is passed to each method
     */
    @Nullable
    default InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return null;
    }

    /**
     * This is called right at the start of query execution, and it's the first step in the instrumentation chain.
     *
     * @param parameters the parameters to this step
     * @param state      the state created during the call to {@link #createStateAsync(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link InstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     */
    @Nullable
    default InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return noOp();
    }

    /**
     * This is called just before a query is parsed.
     *
     * @param parameters the parameters to this step
     * @param state      the state created during the call to {@link #createStateAsync(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link InstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     */
    @Nullable
    default InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return noOp();
    }

    /**
     * This is called just before the parsed query document is validated.
     *
     * @param parameters the parameters to this step
     * @param state      the state created during the call to {@link #createStateAsync(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link InstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     */
    @Nullable
    default InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters, InstrumentationState state) {
        return noOp();
    }

    /**
     * This is called just before the execution of the query operation is started.
     *
     * @param parameters the parameters to this step
     * @param state      the state created during the call to {@link #createStateAsync(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link InstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     */
    @Nullable
    default InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
        return noOp();
    }

    /**
     * This is called each time an {@link graphql.execution.ExecutionStrategy} is invoked, which may be multiple times
     * per query as the engine recursively descends over the query.
     *
     * @param parameters the parameters to this step
     * @param state      the state created during the call to {@link #createStateAsync(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link ExecutionStrategyInstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     */
    @Nullable
    default ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {
        return ExecutionStrategyInstrumentationContext.NOOP;
    }

    /**
     * This is called each time an {@link graphql.execution.ExecutionStrategy} object resolution is called, which may be multiple times
     * per query as the engine recursively descends over the query.
     *
     * @param parameters the parameters to this step
     * @param state      the state created during the call to {@link #createStateAsync(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link ExecutionStrategyInstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     */
    @Nullable
    default ExecuteObjectInstrumentationContext beginExecuteObject(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {
        return ExecuteObjectInstrumentationContext.NOOP;
    }

    /**
     * This is called just before a deferred field is resolved into a value.
     * <p>
     * This is an EXPERIMENTAL instrumentation callback. The method signature will definitely change.
     *
     * @param state the state created during the call to {@link #createStateAsync(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link ExecutionStrategyInstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     */
    @ExperimentalApi
    default InstrumentationContext<Object> beginDeferredField(InstrumentationState state) {
        return noOp();
    }

    /**
     * This is called each time a subscription field produces a new reactive stream event value and it needs to be mapped over via the graphql field subselection.
     *
     * @param parameters the parameters to this step
     * @param state      the state created during the call to {@link #createStateAsync(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link InstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     */
    @Nullable
    default InstrumentationContext<ExecutionResult> beginSubscribedFieldEvent(InstrumentationFieldParameters parameters, InstrumentationState state) {
        return noOp();
    }

    /**
     * This is called just before a field is resolved into a value.
     *
     * @param parameters the parameters to this step
     * @param state      the state created during the call to {@link #createStateAsync(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link InstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     */
    @Nullable
    default InstrumentationContext<Object> beginFieldExecution(InstrumentationFieldParameters parameters, InstrumentationState state) {
        return noOp();
    }


    /**
     * This is called just before a field {@link DataFetcher} is invoked.
     *
     * @param parameters the parameters to this step
     * @param state      the state created during the call to {@link #createStateAsync(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link InstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     *
     * @deprecated use {@link #beginFieldFetching(InstrumentationFieldFetchParameters, InstrumentationState)} instead
     */
    @Deprecated(since = "2024-04-18")
    @Nullable
    default InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        return noOp();
    }

    /**
     * This is called just before a field {@link DataFetcher} is invoked. The {@link FieldFetchingInstrumentationContext#onFetchedValue(Object)}
     * callback will be invoked once a value is returned by a {@link DataFetcher} but perhaps before
     * its value is completed if it's a {@link CompletableFuture} value.
     * <p>
     * This method is the replacement method for the now deprecated {@link #beginFieldFetch(InstrumentationFieldFetchParameters, InstrumentationState)}
     * method, and it should be implemented in new {@link Instrumentation} classes.  This default version of this
     * method calls back to the deprecated  {@link #beginFieldFetch(InstrumentationFieldFetchParameters, InstrumentationState)} method
     * so that older implementations continue to work.
     *
     * @param parameters the parameters to this step
     * @param state      the state created during the call to {@link #createStateAsync(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link InstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     */
    @Nullable
    default FieldFetchingInstrumentationContext beginFieldFetching(InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        InstrumentationContext<Object> ctx = beginFieldFetch(parameters, state);
        return FieldFetchingInstrumentationContext.adapter(ctx);
    }

    /**
     * This is called just before the complete field is started.
     *
     * @param parameters the parameters to this step
     * @param state      the state created during the call to {@link #createStateAsync(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link InstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     */
    @Nullable
    default InstrumentationContext<Object> beginFieldCompletion(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        return noOp();
    }

    /**
     * This is called just before the complete field list is started.
     *
     * @param parameters the parameters to this step
     * @param state      the state created during the call to {@link #createStateAsync(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link InstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     */
    @Nullable
    default InstrumentationContext<Object> beginFieldListCompletion(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        return noOp();
    }

    /**
     * This is called to instrument a {@link graphql.ExecutionInput} before it is used to parse, validate
     * and execute a query, allowing you to adjust what query input parameters are used
     *
     * @param executionInput the execution input to be used
     * @param parameters     the parameters describing the field to be fetched
     * @param state          the state created during the call to {@link #createStateAsync(InstrumentationCreateStateParameters)}
     *
     * @return a non-null instrumented ExecutionInput, the default is to return to the same object
     */
    @NotNull
    default ExecutionInput instrumentExecutionInput(ExecutionInput executionInput, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return executionInput;
    }

    /**
     * This is called to instrument a {@link graphql.language.Document} and variables before it is used allowing you to adjust the query AST if you so desire
     *
     * @param documentAndVariables the document and variables to be used
     * @param parameters           the parameters describing the execution
     * @param state                the state created during the call to {@link #createStateAsync(InstrumentationCreateStateParameters)}
     *
     * @return a non-null instrumented DocumentAndVariables, the default is to return to the same objects
     */
    @NotNull
    default DocumentAndVariables instrumentDocumentAndVariables(DocumentAndVariables documentAndVariables, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return documentAndVariables;
    }

    /**
     * This is called to instrument a {@link graphql.schema.GraphQLSchema} before it is used to parse, validate
     * and execute a query, allowing you to adjust what types are used.
     *
     * @param schema     the schema to be used
     * @param parameters the parameters describing the field to be fetched
     * @param state      the state created during the call to {@link #createStateAsync(InstrumentationCreateStateParameters)}
     *
     * @return a non-null instrumented GraphQLSchema, the default is to return to the same object
     */
    @NotNull
    default GraphQLSchema instrumentSchema(GraphQLSchema schema, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return schema;
    }

    /**
     * This is called to instrument a {@link ExecutionContext} before it is used to execute a query,
     * allowing you to adjust the base data used.
     *
     * @param executionContext the execution context to be used
     * @param parameters       the parameters describing the field to be fetched
     * @param state            the state created during the call to {@link #createStateAsync(InstrumentationCreateStateParameters)}
     *
     * @return a non-null instrumented ExecutionContext, the default is to return to the same object
     */
    @NotNull
    default ExecutionContext instrumentExecutionContext(ExecutionContext executionContext, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return executionContext;
    }

    /**
     * This is called to instrument a {@link DataFetcher} just before it is used to fetch a field, allowing you
     * to adjust what information is passed back or record information about specific data fetches.  Note
     * the same data fetcher instance maybe presented to you many times and that data fetcher
     * implementations widely vary.
     *
     * @param dataFetcher the data fetcher about to be used
     * @param parameters  the parameters describing the field to be fetched
     * @param state       the state created during the call to {@link #createStateAsync(InstrumentationCreateStateParameters)}
     *
     * @return a non-null instrumented DataFetcher, the default is to return to the same object
     */
    @NotNull
    default DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        return dataFetcher;
    }

    /**
     * This is called to allow instrumentation to instrument the execution result in some way
     *
     * @param executionResult {@link java.util.concurrent.CompletableFuture} of the result to instrument
     * @param parameters      the parameters to this step
     * @param state           the state created during the call to {@link #createStateAsync(InstrumentationCreateStateParameters)}
     *
     * @return a new execution result completable future
     */
    @NotNull
    default CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return CompletableFuture.completedFuture(executionResult);
    }
}
