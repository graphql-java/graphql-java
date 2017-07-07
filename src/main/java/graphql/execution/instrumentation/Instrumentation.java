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

import java.util.List;

/**
 * Provides the capability to instrument the execution steps of a GraphQL query.
 *
 * For example you might want to track which fields are taking the most time to fetch from the backing database
 * or log what fields are being asked for.
 */
public interface Instrumentation {

    /**
     * This is called just before a query is executed and when this step finishes the {@link InstrumentationContext#onEnd(Object)}
     * will be called indicating that the step has finished.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters);

    /**
     * This is called just before a query is parsed and when this step finishes the {@link InstrumentationContext#onEnd(Object)}
     * will be called indicating that the step has finished.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters);

    /**
     * This is called just before the parsed query Document is validated and when this step finishes the {@link InstrumentationContext#onEnd(Object)}
     * will be called indicating that the step has finished.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters);

    /**
     * This is called just before the data fetch is started and when this step finishes the {@link InstrumentationContext#onEnd(Object)}
     * will be called indicating that the step has finished.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<ExecutionResult> beginDataFetch(InstrumentationDataFetchParameters parameters);

    /**
     * This is called just before a field is resolved and when this step finishes the {@link InstrumentationContext#onEnd(Object)}
     * will be called indicating that the step has finished.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters);

    /**
     * This is called just before a field {@link DataFetcher} is invoked and when this step finishes the {@link InstrumentationContext#onEnd(Object)}
     * will be called indicating that the step has finished.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters);

    /**
     * This is called to instrument a {@link DataFetcher} just before it is used to fetch a field, allowing you
     * to adjust what information is passed back or record information about specific data fetches.  Note
     * the same data fetcher instance maybe presented to you many times and that data fetcher
     * implementations widely vary.
     *
     * @param dataFetcher the data fetcher about to be used
     *
     * @return a non null instrumented data fetcher, the default is to return to the same object
     */
    default DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher) {
        return dataFetcher;
    }
}
