package graphql;


import graphql.execution.Execution;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionIdProvider;
import graphql.execution.ExecutionStrategy;
import graphql.execution.SimpleExecutionStrategy;
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

    private static final Logger log = LoggerFactory.getLogger(GraphQL.class);

    private static final ExecutionIdProvider DEFAULT_EXECUTION_ID_PROVIDER = new ExecutionIdProvider() {
        @Override
        public ExecutionId provide(String query, String operationName, Object context) {
            return ExecutionId.generate();
        }
    };

    private final GraphQLSchema graphQLSchema;
    private final ExecutionStrategy queryStrategy;
    private final ExecutionStrategy mutationStrategy;
    private final ExecutionIdProvider idProvider;
    private final Instrumentation instrumentation;


    /**
     * A GraphQL object ready to execute queries
     *
     * @param graphQLSchema the schema to use
     *
     * @deprecated use the {@link #newGraphQL(GraphQLSchema)} builder instead.  This will be removed in a future version.
     */
    public GraphQL(GraphQLSchema graphQLSchema) {
        //noinspection deprecation
        this(graphQLSchema, null, null);
    }


    /**
     * A GraphQL object ready to execute queries
     *
     * @param graphQLSchema the schema to use
     * @param queryStrategy the query execution strategy to use
     *
     * @deprecated use the {@link #newGraphQL(GraphQLSchema)} builder instead.  This will be removed in a future version.
     */
    public GraphQL(GraphQLSchema graphQLSchema, ExecutionStrategy queryStrategy) {
        //noinspection deprecation
        this(graphQLSchema, queryStrategy, null);
    }

    /**
     * A GraphQL object ready to execute queries
     *
     * @param graphQLSchema    the schema to use
     * @param queryStrategy    the query execution strategy to use
     * @param mutationStrategy the mutation execution strategy to use
     *
     * @deprecated use the {@link #newGraphQL(GraphQLSchema)} builder instead.  This will be removed in a future version.
     */
    public GraphQL(GraphQLSchema graphQLSchema, ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy) {
        this(graphQLSchema,queryStrategy,mutationStrategy, DEFAULT_EXECUTION_ID_PROVIDER, NoOpInstrumentation.INSTANCE);
    }

    private GraphQL(GraphQLSchema graphQLSchema, ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy, ExecutionIdProvider idProvider, Instrumentation instrumentation) {
        this.graphQLSchema = assertNotNull(graphQLSchema,"queryStrategy must be non null");
        this.queryStrategy = queryStrategy != null ? queryStrategy : new SimpleExecutionStrategy();
        this.mutationStrategy = mutationStrategy != null ? mutationStrategy : new SimpleExecutionStrategy();
        this.idProvider = assertNotNull(idProvider, "idProvider must be non null");
        this.instrumentation = instrumentation;
    }

    /**
     * Helps you build a GraphQL object ready to execute queries
     *
     * @param graphQLSchema the schema to use
     *
     * @return a builder of GraphQL objects
     */
    public static Builder newGraphQL(GraphQLSchema graphQLSchema) {
        return new Builder(graphQLSchema);
    }


    public static class Builder {
        private GraphQLSchema graphQLSchema;
        private ExecutionStrategy queryExecutionStrategy = new SimpleExecutionStrategy();
        private ExecutionStrategy mutationExecutionStrategy = new SimpleExecutionStrategy();
        private ExecutionIdProvider idProvider = DEFAULT_EXECUTION_ID_PROVIDER;
        private Instrumentation instrumentation = NoOpInstrumentation.INSTANCE;


        public Builder(GraphQLSchema graphQLSchema) {
            this.graphQLSchema = graphQLSchema;
        }

        public Builder schema(GraphQLSchema graphQLSchema) {
            this.graphQLSchema = assertNotNull(graphQLSchema, "GraphQLSchema must be non null");
            return this;
        }

        public Builder queryExecutionStrategy(ExecutionStrategy executionStrategy) {
            this.queryExecutionStrategy = assertNotNull(executionStrategy, "Query ExecutionStrategy must be non null");
            return this;
        }

        public Builder mutationExecutionStrategy(ExecutionStrategy executionStrategy) {
            this.mutationExecutionStrategy = assertNotNull(executionStrategy, "Mutation ExecutionStrategy must be non null");
            return this;
        }

        public Builder instrumentation(Instrumentation instrumentation) {
            this.instrumentation = assertNotNull(instrumentation, "Instrumentation must be non null");
            return this;
        }

        public Builder executionIdProvider(ExecutionIdProvider executionIdProvider) {
            this.idProvider = assertNotNull(executionIdProvider, "ExecutionIdProvider must be non null");
            return this;
        }

        public GraphQL build() {
            assertNotNull(graphQLSchema,"queryStrategy must be non null");
            assertNotNull(queryExecutionStrategy, "queryStrategy must be non null");
            assertNotNull(idProvider, "idProvider must be non null");
            return new GraphQL(graphQLSchema, queryExecutionStrategy, mutationExecutionStrategy, idProvider, instrumentation);
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
