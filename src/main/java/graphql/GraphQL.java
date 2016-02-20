package graphql;


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

import static graphql.Assert.assertNotNull;

/**
 * <p>GraphQL class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class GraphQL {


    private final GraphQLSchema graphQLSchema;
    private final ExecutionStrategy executionStrategy;

    private static final Logger log = LoggerFactory.getLogger(GraphQL.class);

    /**
     * <p>Constructor for GraphQL.</p>
     *
     * @param graphQLSchema a {@link graphql.schema.GraphQLSchema} object.
     */
    public GraphQL(GraphQLSchema graphQLSchema) {
        this(graphQLSchema, null);
    }


    /**
     * <p>Constructor for GraphQL.</p>
     *
     * @param graphQLSchema a {@link graphql.schema.GraphQLSchema} object.
     * @param executionStrategy a {@link graphql.execution.ExecutionStrategy} object.
     */
    public GraphQL(GraphQLSchema graphQLSchema, ExecutionStrategy executionStrategy) {
        this.graphQLSchema = graphQLSchema;
        this.executionStrategy = executionStrategy;
    }

    /**
     * <p>execute.</p>
     *
     * @param requestString a {@link java.lang.String} object.
     * @return a {@link graphql.ExecutionResult} object.
     */
    public ExecutionResult execute(String requestString) {
        return execute(requestString, null);
    }

    /**
     * <p>execute.</p>
     *
     * @param requestString a {@link java.lang.String} object.
     * @param context a {@link java.lang.Object} object.
     * @return a {@link graphql.ExecutionResult} object.
     */
    public ExecutionResult execute(String requestString, Object context) {
        return execute(requestString, context, Collections.<String, Object>emptyMap());
    }

    /**
     * <p>execute.</p>
     *
     * @param requestString a {@link java.lang.String} object.
     * @param operationName a {@link java.lang.String} object.
     * @param context a {@link java.lang.Object} object.
     * @return a {@link graphql.ExecutionResult} object.
     */
    public ExecutionResult execute(String requestString, String operationName, Object context) {
        return execute(requestString, operationName, context, Collections.<String, Object>emptyMap());
    }

    /**
     * <p>execute.</p>
     *
     * @param requestString a {@link java.lang.String} object.
     * @param context a {@link java.lang.Object} object.
     * @param arguments a {@link java.util.Map} object.
     * @return a {@link graphql.ExecutionResult} object.
     */
    public ExecutionResult execute(String requestString, Object context, Map<String, Object> arguments) {
        return execute(requestString, null, context, arguments);
    }

    /**
     * <p>execute.</p>
     *
     * @param requestString a {@link java.lang.String} object.
     * @param operationName a {@link java.lang.String} object.
     * @param context a {@link java.lang.Object} object.
     * @param arguments a {@link java.util.Map} object.
     * @return a {@link graphql.ExecutionResult} object.
     */
    public ExecutionResult execute(String requestString, String operationName, Object context, Map<String, Object> arguments) {
        assertNotNull(arguments, "arguments can't be null");
        log.info("Executing request. operation name: {}. Request: {} ", operationName, requestString);
        Parser parser = new Parser();
        Document document;
        try {
            document = parser.parseDocument(requestString);
        } catch (ParseCancellationException e) {
            RecognitionException recognitionException = (RecognitionException) e.getCause();
            SourceLocation sourceLocation = new SourceLocation(recognitionException.getOffendingToken().getLine(), recognitionException.getOffendingToken().getCharPositionInLine());
            InvalidSyntaxError invalidSyntaxError = new InvalidSyntaxError(sourceLocation);
            return new ExecutionResultImpl(Arrays.asList(invalidSyntaxError));
        }

        Validator validator = new Validator();
        List<ValidationError> validationErrors = validator.validateDocument(graphQLSchema, document);
        if (validationErrors.size() > 0) {
            return new ExecutionResultImpl(validationErrors);
        }
        Execution execution = new Execution(executionStrategy);
        return execution.execute(graphQLSchema, context, document, operationName, arguments);
    }


}
