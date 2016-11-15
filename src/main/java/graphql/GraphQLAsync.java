package graphql;

import graphql.execution.AsyncExecution;
import graphql.execution.Execution;
import graphql.execution.ExecutionStrategy;
import graphql.language.Document;
import graphql.language.SourceLocation;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.validation.ValidationError;
import graphql.validation.Validator;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static graphql.Assert.assertNotNull;
import static java.util.concurrent.CompletableFuture.completedFuture;

public class GraphQLAsync extends GraphQL {

    private static Logger log = LoggerFactory.getLogger(GraphQLAsync.class);

    public GraphQLAsync(GraphQLSchema graphQLSchema) {
        super(graphQLSchema);
    }

    public GraphQLAsync(GraphQLSchema graphQLSchema, ExecutionStrategy queryStrategy) {
        super(graphQLSchema, queryStrategy);
    }

    public GraphQLAsync(GraphQLSchema graphQLSchema, ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy) {
        super(graphQLSchema, queryStrategy, mutationStrategy);
    }

    public CompletionStage<ExecutionResult> executeAsync(String requestString) {
        return executeAsync(requestString, null);

    }

    public CompletionStage<ExecutionResult> executeAsync(String requestString, Object context) {
        return executeAsync(requestString, context, Collections.emptyMap());

    }

    public CompletionStage<ExecutionResult> executeAsync(String requestString, String operationName, Object context) {
        return executeAsync(requestString, operationName, context, Collections.emptyMap());

    }

    public CompletionStage<ExecutionResult> executeAsync(String requestString, Object context, Map<String, Object> arguments) {
        return executeAsync(requestString, null, context, arguments);

    }

    @SuppressWarnings("unchecked")
    public CompletionStage<ExecutionResult> executeAsync(String requestString, String operationName, Object context, Map<String, Object> arguments) {

        assertNotNull(arguments, "arguments can't be null");
        log.debug("Executing request. operation name: {}. Request: {} ", operationName, requestString);
        Parser parser = new Parser();
        Document document;
        try {
            document = parser.parseDocument(requestString);
        } catch (ParseCancellationException e) {
            RecognitionException recognitionException = (RecognitionException) e.getCause();
            SourceLocation sourceLocation = new SourceLocation(recognitionException.getOffendingToken().getLine(), recognitionException.getOffendingToken().getCharPositionInLine());
            InvalidSyntaxError invalidSyntaxError = new InvalidSyntaxError(sourceLocation);
            return completedFuture(new ExecutionResultImpl(Arrays.asList(invalidSyntaxError)));
        }

        Validator validator = new Validator();
        List<ValidationError> validationErrors = validator.validateDocument(graphQLSchema, document);
        if (validationErrors.size() > 0) {
            return completedFuture(new ExecutionResultImpl(validationErrors));
        }
        AsyncExecution execution = new AsyncExecution(queryStrategy, mutationStrategy);
        return execution.executeAsync(graphQLSchema, context, document, operationName, arguments);
    }
}
