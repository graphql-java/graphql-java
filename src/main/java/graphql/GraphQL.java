package graphql;


import graphql.execution.Execution;
import graphql.execution.ExecutionStrategy;
import graphql.execution.SimpleExecutionStrategy;
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

    private static final Logger log = LoggerFactory.getLogger(GraphQL.class);

    /**
     * A GraphQL object ready to execute queries
     *
     * @param graphQLSchema the schema to use
     *
     * @deprecated use the {@link #newObject(GraphQLSchema)} builder instead.  This will be removed in a future version.
     */
    public GraphQL(GraphQLSchema graphQLSchema) {
        //noinspection deprecation
        this(graphQLSchema, null, null);
    }


    /**
     * A GraphQL object ready to execute queries
     *
     * @param graphQLSchema     the schema to use
     * @param queryStrategy the execution strategy to use
     *
     * @deprecated use the {@link #newObject(GraphQLSchema)} builder instead.  This will be removed in a future version.
     */
    public GraphQL(GraphQLSchema graphQLSchema, ExecutionStrategy queryStrategy) {
        //noinspection deprecation
        this(graphQLSchema, queryStrategy, null);
    }

    /**
     * A GraphQL object ready to execute queries
     *
     * @param graphQLSchema     the schema to use
     * @param queryStrategy the execution strategy to use
     *
     * @deprecated use the {@link #newObject(GraphQLSchema)} builder instead.  This will be removed in a future version.
     */
    public GraphQL(GraphQLSchema graphQLSchema, ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy) {
        this.graphQLSchema = graphQLSchema;
        this.queryStrategy = queryStrategy;
        this.mutationStrategy = mutationStrategy;
    }

    /**
     * Helps you build GraphQL object ready to execute queries
     *
     * @param graphQLSchema the schema to use
     *
     * @return a builder of GraphQL objects
     */
    public static Builder newObject(GraphQLSchema graphQLSchema) {
        return new Builder(graphQLSchema);
    }


    public static class Builder {
        private GraphQLSchema graphQLSchema;
        private ExecutionStrategy executionStrategy = new SimpleExecutionStrategy();

        public Builder(GraphQLSchema graphQLSchema) {
            this.graphQLSchema = graphQLSchema;
        }

        public Builder schema(GraphQLSchema graphQLSchema) {
            this.graphQLSchema = assertNotNull(graphQLSchema, "GraphQLSchema must be non null");
            return this;
        }

        public Builder executionStrategy(ExecutionStrategy executionStrategy) {
            this.executionStrategy = assertNotNull(executionStrategy, "ExecutionStrategy must be non null");
            return this;
        }

        public GraphQL build() {
            //noinspection deprecation
            return new GraphQL(graphQLSchema, executionStrategy);
        }
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
        Execution execution = new Execution(queryStrategy, mutationStrategy);
        return execution.execute(graphQLSchema, context, document, operationName, arguments);
    }


}
