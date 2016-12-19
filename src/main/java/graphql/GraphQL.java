package graphql;


import graphql.execution.Execution;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionIdProvider;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;

public class GraphQL {


    private final GraphQLSchema graphQLSchema;
    private final ExecutionStrategy queryStrategy;
    private final ExecutionStrategy mutationStrategy;
    //
    // later PR changes will allow api consumers to provide their own id provider
    //
    // see https://github.com/graphql-java/graphql-java/pull/276 for the builder pattern
    // needed to make this sustainable.  But for now we will use a hard coded approach.
    //
    private final ExecutionIdProvider idProvider = new ExecutionIdProvider() {
        @Override
        public ExecutionId generate(String query, String operationName, Object context) {
            return ExecutionId.generate();
        }
    };

    private static final Logger log = LoggerFactory.getLogger(GraphQL.class);

    public GraphQL(GraphQLSchema graphQLSchema) {
        this(graphQLSchema, null, null);
    }


    public GraphQL(GraphQLSchema graphQLSchema, ExecutionStrategy queryStrategy) {
        this(graphQLSchema, queryStrategy, null);
    }

    public GraphQL(GraphQLSchema graphQLSchema, ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy) {
        this.graphQLSchema = graphQLSchema;
        this.queryStrategy = queryStrategy;
        this.mutationStrategy = mutationStrategy;
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
        log.debug("Executing request. operation name: {}. Request: {} ", operationName, requestString);
        Parser parser = new Parser();
        Document document;
        try {
            document = parser.parseDocument(requestString);
        } catch (ParseCancellationException e) {
            RecognitionException recognitionException = (RecognitionException) e.getCause();
            SourceLocation sourceLocation = new SourceLocation(recognitionException.getOffendingToken().getLine(), recognitionException.getOffendingToken().getCharPositionInLine());
            InvalidSyntaxError invalidSyntaxError = new InvalidSyntaxError(sourceLocation);
            return new ExecutionResultImpl(Collections.singletonList(invalidSyntaxError));
        }

        Validator validator = new Validator();
        List<ValidationError> validationErrors = validator.validateDocument(graphQLSchema, document);
        if (validationErrors.size() > 0) {
            return new ExecutionResultImpl(validationErrors);
        }
        ExecutionId executionId = idProvider.generate(requestString, operationName, context);

        Execution execution = new Execution(queryStrategy, mutationStrategy);
        return execution.execute(executionId, graphQLSchema, context, document, operationName, arguments);
    }


}
