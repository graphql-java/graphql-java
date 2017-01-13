package graphql.execution.instrumentation;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.parameters.DataFetchParameters;
import graphql.execution.instrumentation.parameters.ExecutionParameters;
import graphql.execution.instrumentation.parameters.FieldFetchParameters;
import graphql.execution.instrumentation.parameters.FieldParameters;
import graphql.execution.instrumentation.parameters.ValidationParameters;
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
    InstrumentationContext<ExecutionResult> beginExecution(ExecutionParameters parameters);

    /**
     * This is called just before a query is parsed and when this step finishes the {@link InstrumentationContext#onEnd(Object)}
     * will be called indicating that the step has finished.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<Document> beginParse(ExecutionParameters parameters);

    /**
     * This is called just before the parsed query Document is validated and when this step finishes the {@link InstrumentationContext#onEnd(Object)}
     * will be called indicating that the step has finished.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<List<ValidationError>> beginValidation(ValidationParameters parameters);

    /**
     * This is called just before the data fetch is started and when this step finishes the {@link InstrumentationContext#onEnd(Object)}
     * will be called indicating that the step has finished.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<ExecutionResult> beginDataFetch(DataFetchParameters parameters);

    /**
     * This is called just before a field is resolved and when this step finishes the {@link InstrumentationContext#onEnd(Object)}
     * will be called indicating that the step has finished.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<ExecutionResult> beginField(FieldParameters parameters);

    /**
     * This is called just before a field {@link DataFetcher} is invoked and when this step finishes the {@link InstrumentationContext#onEnd(Object)}
     * will be called indicating that the step has finished.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<Object> beginFieldFetch(FieldFetchParameters parameters);
}
