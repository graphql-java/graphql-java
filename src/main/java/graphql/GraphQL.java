package graphql;

import graphql.execution.Execution;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionIdProvider;
import graphql.execution.ExecutionStrategy;
import graphql.execution.SimpleExecutionStrategy;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.NoOpInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
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

@PublicApi
public class GraphQL {

    private static final Logger log = LoggerFactory.getLogger(GraphQL.class);

    private static final ExecutionIdProvider DEFAULT_EXECUTION_ID_PROVIDER = (query, operationName, context) -> ExecutionId.generate();

    private final GraphQLSchema graphQLSchema;
    private final ExecutionStrategy queryStrategy;
    private final ExecutionStrategy mutationStrategy;
    private final ExecutionStrategy subscriptionStrategy;
    private final ExecutionIdProvider idProvider;
    private final Instrumentation instrumentation;


    /**
     * A GraphQL object ready to execute queries
     *
     * @param graphQLSchema the schema to use
     * @deprecated use the {@link #newGraphQL(GraphQLSchema)} builder instead.  This will be removed in a future version.
     */
    @Internal
    public GraphQL(GraphQLSchema graphQLSchema) {
        //noinspection deprecation
        this(graphQLSchema, null, null);
    }

    /**
     * A GraphQL object ready to execute queries
     *
     * @param graphQLSchema the schema to use
     * @param queryStrategy the query execution strategy to use
     * @deprecated use the {@link #newGraphQL(GraphQLSchema)} builder instead.  This will be removed in a future version.
     */
    @Internal
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
     * @deprecated use the {@link #newGraphQL(GraphQLSchema)} builder instead.  This will be removed in a future version.
     */
    @Internal
    public GraphQL(GraphQLSchema graphQLSchema, ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy) {
        this(graphQLSchema, queryStrategy, mutationStrategy, null, DEFAULT_EXECUTION_ID_PROVIDER, NoOpInstrumentation.INSTANCE);
    }

    /**
     * A GraphQL object ready to execute queries
     *
     * @param graphQLSchema        the schema to use
     * @param queryStrategy        the query execution strategy to use
     * @param mutationStrategy     the mutation execution strategy to use
     * @param subscriptionStrategy the subscription execution strategy to use
     * @deprecated use the {@link #newGraphQL(GraphQLSchema)} builder instead.  This will be removed in a future version.
     */
    @Internal
    public GraphQL(GraphQLSchema graphQLSchema, ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy, ExecutionStrategy subscriptionStrategy) {
        this(graphQLSchema, queryStrategy, mutationStrategy, subscriptionStrategy, DEFAULT_EXECUTION_ID_PROVIDER, NoOpInstrumentation.INSTANCE);
    }

    private GraphQL(GraphQLSchema graphQLSchema, ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy, ExecutionStrategy subscriptionStrategy, ExecutionIdProvider idProvider, Instrumentation instrumentation) {
        this.graphQLSchema = assertNotNull(graphQLSchema, "queryStrategy must be non null");
        this.queryStrategy = queryStrategy != null ? queryStrategy : new SimpleExecutionStrategy();
        this.mutationStrategy = mutationStrategy != null ? mutationStrategy : new SimpleExecutionStrategy();
        this.subscriptionStrategy = subscriptionStrategy != null ? subscriptionStrategy : new SimpleExecutionStrategy();
        this.idProvider = assertNotNull(idProvider, "idProvider must be non null");
        this.instrumentation = instrumentation;
    }

    /**
     * Helps you build a GraphQL object ready to execute queries
     *
     * @param graphQLSchema the schema to use
     * @return a builder of GraphQL objects
     */
    public static Builder newGraphQL(GraphQLSchema graphQLSchema) {
        return new Builder(graphQLSchema);
    }


    @PublicApi
    public static class Builder {
        private GraphQLSchema graphQLSchema;
        private ExecutionStrategy queryExecutionStrategy = new SimpleExecutionStrategy();
        private ExecutionStrategy mutationExecutionStrategy = new SimpleExecutionStrategy();
        private ExecutionStrategy subscriptionExecutionStrategy = new SimpleExecutionStrategy();
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

        public Builder subscriptionExecutionStrategy(ExecutionStrategy executionStrategy) {
            this.subscriptionExecutionStrategy = assertNotNull(executionStrategy, "Subscription ExecutionStrategy must be non null");
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
            assertNotNull(graphQLSchema, "queryStrategy must be non null");
            assertNotNull(queryExecutionStrategy, "queryStrategy must be non null");
            assertNotNull(idProvider, "idProvider must be non null");
            return new GraphQL(graphQLSchema, queryExecutionStrategy, mutationExecutionStrategy, subscriptionExecutionStrategy, idProvider, instrumentation);
        }
    }

    /**
     * @param requestString the query/mutation/subscription
     * @return result including errors
     * @deprecated Use {@link #execute(ExecutionInput)}
     */
    @Deprecated
    public ExecutionResult execute(String requestString) {
        return execute(requestString, null);
    }

    /**
     * Info: This sets context = root to be backwards compatible.
     *
     * @param requestString the query/mutation/subscription
     * @param context       custom object provided to each {@link graphql.schema.DataFetcher}
     * @return result including errors
     * @deprecated Use {@link #execute(ExecutionInput)}
     */
    @Deprecated
    public ExecutionResult execute(String requestString, Object context) {
        return execute(requestString, context, Collections.emptyMap());
    }

    /**
     * Info: This sets context = root to be backwards compatible.
     *
     * @param requestString the query/mutation/subscription
     * @param operationName the name of the operation to execute
     * @param context       custom object provided to each {@link graphql.schema.DataFetcher}
     * @return result including errors
     * @deprecated Use {@link #execute(ExecutionInput)}
     */
    @Deprecated
    public ExecutionResult execute(String requestString, String operationName, Object context) {
        return execute(requestString, operationName, context, Collections.emptyMap());
    }

    /**
     * Info: This sets context = root to be backwards compatible.
     *
     * @param requestString the query/mutation/subscription
     * @param context       custom object provided to each {@link graphql.schema.DataFetcher}
     * @param arguments     variable values uses as argument
     * @return result including errors
     * @deprecated Use {@link #execute(ExecutionInput)}
     */
    @Deprecated
    public ExecutionResult execute(String requestString, Object context, Map<String, Object> arguments) {
        return execute(requestString, null, context, arguments);
    }

    /**
     * Info: This sets context = root to be backwards compatible.
     *
     * @param requestString the query/mutation/subscription
     * @param operationName name of the operation to execute
     * @param context       custom object provided to each {@link graphql.schema.DataFetcher}
     * @param arguments     variable values uses as argument
     * @return result including errors
     * @deprecated Use {@link #execute(ExecutionInput)}
     */
    @Deprecated
    public ExecutionResult execute(String requestString, String operationName, Object context, Map<String, Object> arguments) {
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .requestString(requestString)
                .operationName(operationName)
                .context(context)
                .root(context) // This we are doing do be backwards compatible
                .arguments(arguments)
                .build();
        return execute(executionInput);
    }

    /**
     * @param executionInput {@link ExecutionInput}
     * @return result including errors
     */
    public ExecutionResult execute(ExecutionInput executionInput) {
        log.debug("Executing request. operation name: {}. Request: {} ", executionInput.getOperationName(), executionInput.getRequestString());

        InstrumentationContext<ExecutionResult> executionInstrumentation = instrumentation.beginExecution(new InstrumentationExecutionParameters(executionInput));
        final ExecutionResult executionResult = parseValidateAndExecute(executionInput);
        executionInstrumentation.onEnd(executionResult);

        return executionResult;
    }

    private ExecutionResult parseValidateAndExecute(ExecutionInput executionInput) {
        ParseResult parseResult = parse(executionInput);
        if (parseResult.isFailure()) {
            return toParseFailureExecutionResult(parseResult.getException());
        }
        final Document document = parseResult.getData();

        final List<ValidationError> errors = validate(executionInput, document);
        if (!errors.isEmpty()) {
            return new ExecutionResultImpl(errors);
        }

        return execute(executionInput, document);
    }

    private ParseResult parse(ExecutionInput executionInput) {
        InstrumentationContext<Document> parseInstrumentation = instrumentation.beginParse(new InstrumentationExecutionParameters(executionInput));

        Parser parser = new Parser();
        Document document;
        try {
            document = parser.parseDocument(executionInput.getRequestString());
        } catch (ParseCancellationException e) {
            parseInstrumentation.onEnd(e);
            return ParseResult.ofError((RecognitionException) e.getCause());
        }

        parseInstrumentation.onEnd(document);
        return ParseResult.of(document);
    }

    private List<ValidationError> validate(ExecutionInput executionInput, Document document) {
        InstrumentationContext<List<ValidationError>> validationCtx = instrumentation.beginValidation(new InstrumentationValidationParameters(executionInput, document));

        Validator validator = new Validator();
        List<ValidationError> validationErrors = validator.validateDocument(graphQLSchema, document);

        validationCtx.onEnd(validationErrors);
        return validationErrors;
    }

    private ExecutionResult execute(ExecutionInput executionInput, Document document) {
        String requestString = executionInput.getRequestString();
        String operationName = executionInput.getOperationName();
        Object context = executionInput.getContext();
        Object root = executionInput.getRoot();
        Map<String, Object> arguments = executionInput.getArguments() != null ? executionInput.getArguments() : Collections.emptyMap();

        Execution execution = new Execution(queryStrategy, mutationStrategy, subscriptionStrategy, instrumentation);
        ExecutionId executionId = idProvider.provide(requestString, operationName, context);
        return execution.execute(executionId, graphQLSchema, context, root, document, operationName, arguments);
    }

    private static class ParseResult {
        private final Document data;
        private final RecognitionException exception;

        ParseResult(Document data, RecognitionException exception) {
            this.data = data;
            this.exception = exception;
        }

        boolean isFailure() {
            return data == null;
        }

        public Document getData() {
            return data;
        }

        public RecognitionException getException() {
            return exception;
        }

        static ParseResult of(Document result) {
            return new ParseResult(result, null);
        }

        static ParseResult ofError(RecognitionException e) {
            return new ParseResult(null, e);
        }
    }

    private ExecutionResult toParseFailureExecutionResult(RecognitionException exception) {
        InvalidSyntaxError invalidSyntaxError = toInvalidSyntaxError(exception);
        return new ExecutionResultImpl(invalidSyntaxError);
    }

    private InvalidSyntaxError toInvalidSyntaxError(RecognitionException recognitionException) {
        SourceLocation sourceLocation = null;
        if (recognitionException != null) {
            sourceLocation = new SourceLocation(recognitionException.getOffendingToken().getLine(), recognitionException.getOffendingToken().getCharPositionInLine());
        }
        return new InvalidSyntaxError(sourceLocation);
    }
}
