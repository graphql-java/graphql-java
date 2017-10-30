package graphql.execution.instrumentation;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Provides the capability to instrument the execution steps of a GraphQL query.
 *
 * For example you might want to track which fields are taking the most time to fetch from the backing database
 * or log what fields are being asked for.
 *
 * Remember that graphql calls can cross threads so make sure you think about the thread safety of any instrumentation
 * code when you are writing it.
 */
public interface Instrumentation {

    /**
     * This will be called just before execution to create an object that is given back to all instrumentation methods
     * to allow them to have per execution request state
     *
     * @param parameters the parameters to this step
     *
     * @return a state object that is passed to each method
     */
    default InstrumentationPreExecutionState createPreExecutionState(InstrumentationCreatePreExecutionStateParameters parameters) {
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
    default InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return null;
    }

    /**
     * This is called just before a query is executed and when this step finishes the {@link InstrumentationContext#onEnd(Object, Throwable)}
     * will be called indicating that the step has finished.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters);

    /**
     * This is called just before a query is parsed and when this step finishes the {@link InstrumentationContext#onEnd(Object, Throwable)}
     * will be called indicating that the step has finished.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters);

    /**
     * This is called just before the parsed query Document is validated and when this step finishes the {@link InstrumentationContext#onEnd(Object, Throwable)}
     * will be called indicating that the step has finished.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters);

    /**
     * This is called just before the data fetching stage is started and finishes as soon as the query is dispatched ready for completion.  This
     * is different to {@link #beginDataFetch(graphql.execution.instrumentation.parameters.InstrumentationDataFetchParameters)}
     * in that this step does not wait for the values to be completed, only dispatched for completion.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    default InstrumentationContext<CompletableFuture<ExecutionResult>> beginDataFetchDispatch(InstrumentationDataFetchParameters parameters) {
        return (result, t) -> {
        };
    }

    /**
     * This is called just before the data fetching stage is started, waits for all data to be completed and when this step finishes the {@link InstrumentationContext#onEnd(Object, Throwable)}
     * will be called indicating that the step has finished.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<ExecutionResult> beginDataFetch(InstrumentationDataFetchParameters parameters);

    /**
     * This is called each time the {@link graphql.execution.ExecutionStrategy} is invoked and when the
     * {@link java.util.concurrent.CompletableFuture} has been dispatched for the query fields the
     * {@link graphql.execution.instrumentation.InstrumentationContext#onEnd(Object, Throwable)}
     * is called.
     *
     * Note because the execution strategy execution is asynchronous, the query data is not guaranteed to be
     * completed when this step finishes.  It is however a chance to dispatch side effects that might cause
     * asynchronous data fetching code to actually run or attach CompletableFuture handlers onto the result
     * via Instrumentation.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<CompletableFuture<ExecutionResult>> beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters);

    /**
     * This is called just before a selection set of fields is resolved and when this step finishes the {@link InstrumentationContext#onEnd(Object, Throwable)}
     * will be called indicating that the step has finished.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    default InstrumentationContext<Map<String, List<Field>>> beginFields(InstrumentationExecutionStrategyParameters parameters) {
        return (result, t) -> {
        };
    }

    /**
     * This is called just before a field is resolved and when this step finishes the {@link InstrumentationContext#onEnd(Object, Throwable)}
     * will be called indicating that the step has finished.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters);

    /**
     * This is called just before a field {@link DataFetcher} is invoked and when this step finishes the {@link InstrumentationContext#onEnd(Object, Throwable)}
     * will be called indicating that the step has finished.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters);


    /**
     * This is called just before the complete field is started and when this step finishes the {@link InstrumentationContext#onEnd(Object, Throwable)}
     * will be called indicating that the step has finished.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    default InstrumentationContext<CompletableFuture<ExecutionResult>> beginCompleteField(InstrumentationFieldCompleteParameters parameters) {
        return (result, t) -> {
        };
    }

    /**
     * This is called just before the complete field list is started and when this step finishes the {@link InstrumentationContext#onEnd(Object, Throwable)}
     * will be called indicating that the step has finished.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    default InstrumentationContext<CompletableFuture<ExecutionResult>> beginCompleteFieldList(InstrumentationFieldCompleteParameters parameters) {
        return (result, t) -> {
        };
    }

    /**
     * This is called to instrument a {@link graphql.ExecutionInput} before it is used to parse, validate
     * and execute a query, allowing you to adjust what query input parameters are used
     *
     * @param executionInput the execution input to be used
     * @param parameters     the parameters describing the field to be fetched
     *
     * @return a non null instrumented ExecutionInput, the default is to return to the same object
     */
    default ExecutionInput instrumentExecutionInput(ExecutionInput executionInput, InstrumentationExecutionParameters parameters) {
        return executionInput;
    }

    /**
     * This is called to instrument a {@link graphql.schema.GraphQLSchema} before it is used to parse, validate
     * and execute a query, allowing you to adjust what types are used.
     *
     * @param schema     the schema to be used
     * @param parameters the parameters describing the field to be fetched
     *
     * @return a non null instrumented GraphQLSchema, the default is to return to the same object
     */
    default GraphQLSchema instrumentSchema(GraphQLSchema schema, InstrumentationExecutionParameters parameters) {
        return schema;
    }

    /**
     * This is called to instrument a {@link ExecutionContext} before it is used to execute a query,
     * allowing you to adjust the base data used.
     *
     * @param executionContext the execution context to be used
     * @param parameters       the parameters describing the field to be fetched
     *
     * @return a non null instrumented ExecutionContext, the default is to return to the same object
     */
    default ExecutionContext instrumentExecutionContext(ExecutionContext executionContext, InstrumentationExecutionContextParameters parameters) {
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
     *
     * @return a non null instrumented DataFetcher, the default is to return to the same object
     */
    default DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
        return dataFetcher;
    }

    /**
     * This is called to allow instrumentation to instrument the execution result in some way
     *
     * @param executionResult {@link java.util.concurrent.CompletableFuture} of the result to instrument
     * @param parameters      the parameters to this step
     *
     * @return a new execution result completable future
     */
    default CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionResultParameters parameters) {
        return CompletableFuture.completedFuture(executionResult);
    }

    /**
     * This is called to allow instrumentation to instrument the final execution result in some way
     *
     * @param executionResult {@link java.util.concurrent.CompletableFuture} of the result to instrument
     * @param parameters      the parameters to this step
     *
     * @return a new execution result completable future
     */
    default CompletableFuture<ExecutionResult> instrumentFinalExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
        return CompletableFuture.completedFuture(executionResult);
    }
}
