package graphql;


import graphql.execution.Execution;
import graphql.language.Document;
import graphql.language.SourceLocation;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.validation.ValidationError;
import graphql.validation.Validator;
import org.antlr.v4.runtime.RecognitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static graphql.Assert.assertNotNull;

public class GraphQL {


    private final GraphQLSchema graphQLSchema;
    private final ExecutorService executorService;

    private static final Logger log = LoggerFactory.getLogger(GraphQL.class);

    public GraphQL(GraphQLSchema graphQLSchema) {
        this(graphQLSchema, null);
    }


    public GraphQL(GraphQLSchema graphQLSchema, ExecutorService executorService) {
        this.graphQLSchema = graphQLSchema;
        this.executorService = executorService;
    }

    public ExecutionResult execute(String requestString) {
        return execute(requestString, null);
    }

    public ExecutionResult execute(String requestString, Object context) {
        return execute(requestString, context, Collections.<String, Object>emptyMap());
    }

    public ExecutionResult execute(String requestString, String operationName, Object context) {
        return execute(requestString, operationName, context, Collections.<String, Object>emptyMap());
    }

    public ExecutionResult execute(String requestString, Object context, Map<String, Object> arguments) {
        return execute(requestString, null, context, arguments);
    }

    public ExecutionResult execute(String requestString, String operationName, Object context, Map<String, Object> arguments) {
        assertNotNull(arguments, "arguments can't be null");
        log.info("Executing request. operation name: {}. Request: {} ", operationName, requestString);
        Parser parser = new Parser();
        Document document;
        try {
            document = parser.parseDocument(requestString);
        } catch (RecognitionException e) {
            SourceLocation sourceLocation = new SourceLocation(e.getOffendingToken().getLine(), e.getOffendingToken().getCharPositionInLine());
            InvalidSyntaxError invalidSyntaxError = new InvalidSyntaxError(sourceLocation);
            return new ExecutionResultImpl(Arrays.asList(invalidSyntaxError));
        }

        Validator validator = new Validator();
        List<ValidationError> validationErrors = validator.validateDocument(graphQLSchema, document, arguments);
        if (validationErrors.size() > 0) {
            return new ExecutionResultImpl(validationErrors);
        }
        Execution execution = new Execution(executorService);
        return execution.execute(graphQLSchema, context, document, operationName, arguments);
    }


}
