package graphql.execution.instrumentation;

import graphql.ExecutionResult;
import graphql.execution.ExecutionContext;
import graphql.language.Document;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.validation.ValidationError;

import java.util.List;
import java.util.Map;

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
     * @param requestString the GraphQL query string
     * @param operationName the GraphQL operation name
     * @param context the context object in play
     * @param arguments the arguments in play
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<ExecutionResult> beginExecution(String requestString, String operationName, Object context, Map<String, Object> arguments);

    /**
     * This is called just before a query is parsed and when this step finishes the {@link InstrumentationContext#onEnd(Object)}
     * will be called indicating that the step has finished.
     *
     * @param requestString the GraphQL query string
     * @param operationName the GraphQL operation name
     * @param context the context object in play
     * @param arguments the arguments in play
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<Document> beginParse(String requestString, String operationName, Object context, Map<String, Object> arguments);

    /**
     * This is called just before the parsed query Document is validated and when this step finishes the {@link InstrumentationContext#onEnd(Object)}
     * will be called indicating that the step has finished.
     *
     * @param document the GraphQL query string
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<List<ValidationError>> beginValidation(Document document);

    /**
     * This is called just before a field is resolved and when this step finishes the {@link InstrumentationContext#onEnd(Object)}
     * will be called indicating that the step has finished.
     *
     * @param executionContext the {@link ExecutionContext} in play
     * @param fieldDef the current {@link GraphQLFieldDefinition} that is about to be resolved
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<ExecutionResult> beginField(ExecutionContext executionContext, GraphQLFieldDefinition fieldDef);

    /**
     * This is called just before a field {@link DataFetcher} is invoked and when this step finishes the {@link InstrumentationContext#onEnd(Object)}
     * will be called indicating that the step has finished.
     *
     * @param executionContext the {@link ExecutionContext} in play
     * @param fieldDef the current {@link GraphQLFieldDefinition} that is about to be resolved
     * @param environment the current {@link DataFetchingEnvironment} that will be passed to the data fetcher
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<Object> beginDataFetch(ExecutionContext executionContext, GraphQLFieldDefinition fieldDef, DataFetchingEnvironment environment);
}
