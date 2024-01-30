package graphql.execution.instrumentation;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.PublicSpi;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.adapters.ExecutionResultInstrumentationContextAdapter;
import graphql.execution.instrumentation.adapters.ExecuteObjectInstrumentationContextAdapter;
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
     * This will be called just before execution to create an object that is given back to all instrumentation methods
     * to allow them to have per execution request state
     *
     * @return a state object that is passed to each method
     *
     * @deprecated use {@link #createState(InstrumentationCreateStateParameters)} instead
     */
    @Deprecated(since = "2022-07-26")
    default InstrumentationState createState() {
        return null;
    }

    /**
     * This will be called just before execution to create an object that is given back to all instrumentation methods
     * to allow them to have per execution request state
     *
     * @param parameters the parameters to this step
     *
     * @return a state object that is passed to each method
     */
    @Deprecated(since = "2023-08-25")
    @Nullable
    default InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return createState();
    }

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
        return CompletableFuture.completedFuture(createState(parameters));
    }

    /**
     * This is called right at the start of query execution, and it's the first step in the instrumentation chain.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     *
     * @deprecated use {@link #beginExecution(InstrumentationExecutionParameters, InstrumentationState)} instead
     */
    @Deprecated(since = "2022-07-26")
    @NotNull
    default InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters) {
        return noOp();
    }

    /**
     * This is called right at the start of query execution, and it's the first step in the instrumentation chain.
     *
     * @param parameters the parameters to this step
     * @param state      the state created during the call to {@link #createState(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link InstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     */
    @Nullable
    default InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return beginExecution(parameters.withNewState(state));
    }

    /**
     * This is called just before a query is parsed.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     *
     * @deprecated use {@link #beginParse(InstrumentationExecutionParameters, InstrumentationState)}  instead
     */
    @Deprecated(since = "2022-07-26")
    @NotNull
    default InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters) {
        return noOp();
    }

    /**
     * This is called just before a query is parsed.
     *
     * @param parameters the parameters to this step
     * @param state      the state created during the call to {@link #createState(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link InstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     */
    @Nullable
    default InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return beginParse(parameters.withNewState(state));
    }

    /**
     * This is called just before the parsed query document is validated.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     *
     * @deprecated use {@link #beginValidation(InstrumentationValidationParameters, InstrumentationState)} instead
     */
    @Deprecated(since = "2022-07-26")
    @NotNull
    default InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
        return noOp();
    }

    /**
     * This is called just before the parsed query document is validated.
     *
     * @param parameters the parameters to this step
     * @param state      the state created during the call to {@link #createState(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link InstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     */
    @Nullable
    default InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters, InstrumentationState state) {
        return beginValidation(parameters.withNewState(state));
    }

    /**
     * This is called just before the execution of the query operation is started.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     *
     * @deprecated use {@link #beginExecuteOperation(InstrumentationExecuteOperationParameters, InstrumentationState)} instead
     */
    @Deprecated(since = "2022-07-26")
    @NotNull
    default InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters) {
        return noOp();
    }

    /**
     * This is called just before the execution of the query operation is started.
     *
     * @param parameters the parameters to this step
     * @param state      the state created during the call to {@link #createState(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link InstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     */
    @Nullable
    default InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
        return beginExecuteOperation(parameters.withNewState(state));
    }

    /**
     * This is called each time an {@link graphql.execution.ExecutionStrategy} is invoked, which may be multiple times
     * per query as the engine recursively descends over the query.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link ExecutionStrategyInstrumentationContext} object that will be called back when the step ends
     *
     * @deprecated use {@link #beginExecutionStrategy(InstrumentationExecutionStrategyParameters, InstrumentationState)} instead
     */
    @Deprecated(since = "2022-07-26")
    @NotNull
    default ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        return ExecutionStrategyInstrumentationContext.NOOP;
    }

    /**
     * This is called each time an {@link graphql.execution.ExecutionStrategy} is invoked, which may be multiple times
     * per query as the engine recursively descends over the query.
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
     * This is called each time an {@link graphql.execution.ExecutionStrategy} object resolution is called, which may be multiple times
     * per query as the engine recursively descends over the query.
     *
     * @param parameters the parameters to this step
     * @param state      the state created during the call to {@link #createState(InstrumentationCreateStateParameters)}
     *
     * @return a nullable {@link ExecutionStrategyInstrumentationContext} object that will be called back when the step ends (assuming it's not null)
     */
    @Nullable
    default ExecuteObjectInstrumentationContext beginExecuteObject(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {
        return ExecuteObjectInstrumentationContext.NOOP;
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
    @Deprecated(since = "2022-07-26")
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
    @Deprecated(since = "2022-07-26")
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
    @Deprecated(since="2023-09-11" )
    @Nullable
    default InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters, InstrumentationState state) {
        return beginField(parameters.withNewState(state));
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
    default InstrumentationContext<Object> beginFieldExecution(InstrumentationFieldParameters parameters, InstrumentationState state) {
        InstrumentationContext<ExecutionResult> ic = beginField(parameters, state);
        return ic == null ? null : new ExecutionResultInstrumentationContextAdapter(ic);
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
    @Deprecated(since = "2022-07-26")
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
    @Deprecated(since = "2022-07-26")
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
    @Deprecated(since = "2023-09-11")
    @Nullable
    default InstrumentationContext<ExecutionResult> beginFieldComplete(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        return beginFieldComplete(parameters.withNewState(state));
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
    default InstrumentationContext<Object> beginFieldCompletion(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        InstrumentationContext<ExecutionResult> ic = beginFieldComplete(parameters, state);
        return ic == null ? null : new ExecutionResultInstrumentationContextAdapter(ic);
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
    @Deprecated(since = "2022-07-26")
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
    @Deprecated(since = "2023-09-11")
    @Nullable
    default InstrumentationContext<ExecutionResult> beginFieldListComplete(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        return beginFieldListComplete(parameters.withNewState(state));
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
    default InstrumentationContext<Object> beginFieldListCompletion(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        InstrumentationContext<ExecutionResult> ic = beginFieldListComplete(parameters, state);
        return ic == null ? null : new ExecutionResultInstrumentationContextAdapter(ic);
    }

    /**
     * This is called to instrument a {@link graphql.ExecutionInput} before it is used to parse, validate
     * and execute a query, allowing you to adjust what query input parameters are used
     *
     * @param executionInput the execution input to be used
     * @param parameters     the parameters describing the field to be fetched
     *
     * @return a non null instrumented ExecutionInput, the default is to return to the same object
     *
     * @deprecated use {@link #instrumentExecutionInput(ExecutionInput, InstrumentationExecutionParameters, InstrumentationState)} instead
     */
    @Deprecated(since = "2022-07-26")
    @NotNull
    default ExecutionInput instrumentExecutionInput(ExecutionInput executionInput, InstrumentationExecutionParameters parameters) {
        return executionInput;
    }

    /**
     * This is called to instrument a {@link graphql.ExecutionInput} before it is used to parse, validate
     * and execute a query, allowing you to adjust what query input parameters are used
     *
     * @param executionInput the execution input to be used
     * @param parameters     the parameters describing the field to be fetched
     * @param state          the state created during the call to {@link #createState(InstrumentationCreateStateParameters)}
     *
     * @return a non null instrumented ExecutionInput, the default is to return to the same object
     */
    @NotNull
    default ExecutionInput instrumentExecutionInput(ExecutionInput executionInput, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return instrumentExecutionInput(executionInput, parameters.withNewState(state));
    }

    /**
     * This is called to instrument a {@link graphql.language.Document} and variables before it is used allowing you to adjust the query AST if you so desire
     *
     * @param documentAndVariables the document and variables to be used
     * @param parameters           the parameters describing the execution
     *
     * @return a non null instrumented DocumentAndVariables, the default is to return to the same objects
     *
     * @deprecated use {@link #instrumentDocumentAndVariables(DocumentAndVariables, InstrumentationExecutionParameters, InstrumentationState)}  instead
     */
    @Deprecated(since = "2022-07-26")
    @NotNull
    default DocumentAndVariables instrumentDocumentAndVariables(DocumentAndVariables documentAndVariables, InstrumentationExecutionParameters parameters) {
        return documentAndVariables;
    }

    /**
     * This is called to instrument a {@link graphql.language.Document} and variables before it is used allowing you to adjust the query AST if you so desire
     *
     * @param documentAndVariables the document and variables to be used
     * @param parameters           the parameters describing the execution
     * @param state                the state created during the call to {@link #createState(InstrumentationCreateStateParameters)}
     *
     * @return a non null instrumented DocumentAndVariables, the default is to return to the same objects
     */
    @NotNull
    default DocumentAndVariables instrumentDocumentAndVariables(DocumentAndVariables documentAndVariables, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return instrumentDocumentAndVariables(documentAndVariables, parameters.withNewState(state));
    }

    /**
     * This is called to instrument a {@link graphql.schema.GraphQLSchema} before it is used to parse, validate
     * and execute a query, allowing you to adjust what types are used.
     *
     * @param schema     the schema to be used
     * @param parameters the parameters describing the field to be fetched
     *
     * @return a non null instrumented GraphQLSchema, the default is to return to the same object
     *
     * @deprecated use {@link #instrumentSchema(GraphQLSchema, InstrumentationExecutionParameters, InstrumentationState)}  instead
     */
    @Deprecated(since = "2022-07-26")
    @NotNull
    default GraphQLSchema instrumentSchema(GraphQLSchema schema, InstrumentationExecutionParameters parameters) {
        return schema;
    }

    /**
     * This is called to instrument a {@link graphql.schema.GraphQLSchema} before it is used to parse, validate
     * and execute a query, allowing you to adjust what types are used.
     *
     * @param schema     the schema to be used
     * @param parameters the parameters describing the field to be fetched
     * @param state      the state created during the call to {@link #createState(InstrumentationCreateStateParameters)}
     *
     * @return a non null instrumented GraphQLSchema, the default is to return to the same object
     */
    @NotNull
    default GraphQLSchema instrumentSchema(GraphQLSchema schema, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return instrumentSchema(schema, parameters.withNewState(state));
    }

    /**
     * This is called to instrument a {@link ExecutionContext} before it is used to execute a query,
     * allowing you to adjust the base data used.
     *
     * @param executionContext the execution context to be used
     * @param parameters       the parameters describing the field to be fetched
     *
     * @return a non null instrumented ExecutionContext, the default is to return to the same object
     *
     * @deprecated use {@link #instrumentExecutionContext(ExecutionContext, InstrumentationExecutionParameters, InstrumentationState)} instead
     */
    @Deprecated(since = "2022-07-26")
    @NotNull
    default ExecutionContext instrumentExecutionContext(ExecutionContext executionContext, InstrumentationExecutionParameters parameters) {
        return executionContext;
    }

    /**
     * This is called to instrument a {@link ExecutionContext} before it is used to execute a query,
     * allowing you to adjust the base data used.
     *
     * @param executionContext the execution context to be used
     * @param parameters       the parameters describing the field to be fetched
     * @param state            the state created during the call to {@link #createState(InstrumentationCreateStateParameters)}
     *
     * @return a non null instrumented ExecutionContext, the default is to return to the same object
     */
    @NotNull
    default ExecutionContext instrumentExecutionContext(ExecutionContext executionContext, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return instrumentExecutionContext(executionContext, parameters.withNewState(state));
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
    @Deprecated(since = "2022-07-26")
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

    /**
     * This is called to allow instrumentation to instrument the execution result in some way
     *
     * @param executionResult {@link java.util.concurrent.CompletableFuture} of the result to instrument
     * @param parameters      the parameters to this step
     *
     * @return a new execution result completable future
     *
     * @deprecated use {@link #instrumentExecutionResult(ExecutionResult, InstrumentationExecutionParameters, InstrumentationState)}   instead
     */
    @Deprecated(since = "2022-07-26")
    @NotNull
    default CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
        return CompletableFuture.completedFuture(executionResult);
    }

    /**
     * This is called to allow instrumentation to instrument the execution result in some way
     *
     * @param executionResult {@link java.util.concurrent.CompletableFuture} of the result to instrument
     * @param parameters      the parameters to this step
     * @param state           the state created during the call to {@link #createState(InstrumentationCreateStateParameters)}
     *
     * @return a new execution result completable future
     */
    @NotNull
    default CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return instrumentExecutionResult(executionResult, parameters.withNewState(state));
    }
}
