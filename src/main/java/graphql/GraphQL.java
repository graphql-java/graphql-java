package graphql;


import graphql.execution.Execution;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionIdProvider;
import graphql.execution.ExecutionStrategy;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.NoOpInstrumentation;
import graphql.execution.instrumentation.parameters.ExecutionParameters;
import graphql.execution.instrumentation.parameters.ValidationParameters;
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
    private final Instrumentation instrumentation;

    //
    // later PR changes will allow api consumers to provide their own id provider
    //
    // see https://github.com/graphql-java/graphql-java/pull/276 for the builder pattern
    // needed to make this sustainable.  But for now we will use a hard coded approach.
    //
    private final ExecutionIdProvider idProvider = new ExecutionIdProvider() {
        @Override
        public ExecutionId provide(String query, String operationName, Object context) {
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
        this(graphQLSchema,queryStrategy,mutationStrategy,NoOpInstrumentation.INSTANCE);
    }

    public GraphQL(GraphQLSchema graphQLSchema, ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy, Instrumentation instrumentation) {
        this.graphQLSchema = graphQLSchema;
        this.queryStrategy = queryStrategy;
        this.mutationStrategy = mutationStrategy;
        this.instrumentation = instrumentation;
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
        InstrumentationContext<ExecutionResult> executionCtx = instrumentation.beginExecution(new ExecutionParameters(requestString, operationName, context, arguments));

        assertNotNull(arguments, "arguments can't be null");
        log.debug("Executing request. operation name: {}. Request: {} ", operationName, requestString);


        InstrumentationContext<Document> parseCtx = instrumentation.beginParse(new ExecutionParameters(requestString, operationName, context, arguments));
        Parser parser = new Parser();
        Document document;
        try {
            document = parser.parseDocument(requestString);
            parseCtx.onEnd(document);
        } catch (ParseCancellationException e) {
            RecognitionException recognitionException = (RecognitionException) e.getCause();
            SourceLocation sourceLocation = new SourceLocation(recognitionException.getOffendingToken().getLine(), recognitionException.getOffendingToken().getCharPositionInLine());
            InvalidSyntaxError invalidSyntaxError = new InvalidSyntaxError(sourceLocation);
            return new ExecutionResultImpl(Collections.singletonList(invalidSyntaxError));
        }

        InstrumentationContext<List<ValidationError>> validationCtx = instrumentation.beginValidation(new ValidationParameters(requestString,operationName,context,arguments,document));

        Validator validator = new Validator();
        List<ValidationError> validationErrors = validator.validateDocument(graphQLSchema, document);

        validationCtx.onEnd(validationErrors);

        if (validationErrors.size() > 0) {
            return new ExecutionResultImpl(validationErrors);
        }
        ExecutionId executionId = idProvider.provide(requestString, operationName, context);

        Execution execution = new Execution(queryStrategy, mutationStrategy, instrumentation);
        ExecutionResult result = execution.execute(executionId, graphQLSchema, context, document, operationName, arguments);

        executionCtx.onEnd(result);

        return result;
    }
}
