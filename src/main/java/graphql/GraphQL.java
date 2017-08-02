package graphql;

import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.AsyncSerialExecutionStrategy;
import graphql.execution.Execution;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionIdProvider;
import graphql.execution.ExecutionStrategy;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.NoOpInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.execution.preparsed.NoOpPreparsedDocumentProvider;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

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
    private final PreparsedDocumentProvider preparsedDocumentProvider;


    /**
     * A GraphQL object ready to execute queries
     *
     * @param graphQLSchema the schema to use
     *
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
     *
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
     *
     * @deprecated use the {@link #newGraphQL(GraphQLSchema)} builder instead.  This will be removed in a future version.
     */
    @Internal
    public GraphQL(GraphQLSchema graphQLSchema, ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy) {
        this(graphQLSchema, queryStrategy, mutationStrategy, null, DEFAULT_EXECUTION_ID_PROVIDER, NoOpInstrumentation.INSTANCE, NoOpPreparsedDocumentProvider.INSTANCE);
    }

    /**
     * A GraphQL object ready to execute queries
     *
     * @param graphQLSchema        the schema to use
     * @param queryStrategy        the query execution strategy to use
     * @param mutationStrategy     the mutation execution strategy to use
     * @param subscriptionStrategy the subscription execution strategy to use
     *
     * @deprecated use the {@link #newGraphQL(GraphQLSchema)} builder instead.  This will be removed in a future version.
     */
    @Internal
    public GraphQL(GraphQLSchema graphQLSchema, ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy, ExecutionStrategy subscriptionStrategy) {
        this(graphQLSchema, queryStrategy, mutationStrategy, subscriptionStrategy, DEFAULT_EXECUTION_ID_PROVIDER, NoOpInstrumentation.INSTANCE, NoOpPreparsedDocumentProvider.INSTANCE);
    }

    private GraphQL(GraphQLSchema graphQLSchema, ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy, ExecutionStrategy subscriptionStrategy, ExecutionIdProvider idProvider, Instrumentation instrumentation, PreparsedDocumentProvider preparsedDocumentProvider) {
        this.graphQLSchema = assertNotNull(graphQLSchema, "queryStrategy must be non null");
        this.queryStrategy = queryStrategy != null ? queryStrategy : new AsyncExecutionStrategy();
        this.mutationStrategy = mutationStrategy != null ? mutationStrategy : new AsyncSerialExecutionStrategy();
        this.subscriptionStrategy = subscriptionStrategy != null ? subscriptionStrategy : new AsyncExecutionStrategy();
        this.idProvider = assertNotNull(idProvider, "idProvider must be non null");
        this.instrumentation = instrumentation;
        this.preparsedDocumentProvider = assertNotNull(preparsedDocumentProvider, "preparsedDocumentProvider must be non null");
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


    @PublicApi
    public static class Builder {
        private GraphQLSchema graphQLSchema;
        private ExecutionStrategy queryExecutionStrategy = new AsyncExecutionStrategy();
        private ExecutionStrategy mutationExecutionStrategy = new AsyncSerialExecutionStrategy();
        private ExecutionStrategy subscriptionExecutionStrategy = new AsyncExecutionStrategy();
        private ExecutionIdProvider idProvider = DEFAULT_EXECUTION_ID_PROVIDER;
        private Instrumentation instrumentation = NoOpInstrumentation.INSTANCE;
        private PreparsedDocumentProvider preparsedDocumentProvider = NoOpPreparsedDocumentProvider.INSTANCE;


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

        public Builder preparsedDocumentProvider(PreparsedDocumentProvider preparsedDocumentProvider) {
            this.preparsedDocumentProvider = assertNotNull(preparsedDocumentProvider, "PreparsedDocumentProvider must be non null");
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
            return new GraphQL(graphQLSchema, queryExecutionStrategy, mutationExecutionStrategy, subscriptionExecutionStrategy, idProvider, instrumentation, preparsedDocumentProvider);
        }
    }

    /**
     * Executes the specified graphql query/mutation/subscription
     *
     * @param query the query/mutation/subscription
     *
     * @return an {@link ExecutionResult} which can include errors
     */
    public ExecutionResult execute(String query) {
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .build();
        return execute(executionInput);
    }

    /**
     * Info: This sets context = root to be backwards compatible.
     *
     * @param query   the query/mutation/subscription
     * @param context custom object provided to each {@link graphql.schema.DataFetcher}
     *
     * @return an {@link ExecutionResult} which can include errors
     *
     * @deprecated Use {@link #execute(ExecutionInput)}
     */
    @Deprecated
    public ExecutionResult execute(String query, Object context) {
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .context(context)
                .root(context) // This we are doing do be backwards compatible
                .build();
        return execute(executionInput);
    }

    /**
     * Info: This sets context = root to be backwards compatible.
     *
     * @param query         the query/mutation/subscription
     * @param operationName the name of the operation to execute
     * @param context       custom object provided to each {@link graphql.schema.DataFetcher}
     *
     * @return an {@link ExecutionResult} which can include errors
     *
     * @deprecated Use {@link #execute(ExecutionInput)}
     */
    @Deprecated
    public ExecutionResult execute(String query, String operationName, Object context) {
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .operationName(operationName)
                .context(context)
                .root(context) // This we are doing do be backwards compatible
                .build();
        return execute(executionInput);
    }

    /**
     * Info: This sets context = root to be backwards compatible.
     *
     * @param query     the query/mutation/subscription
     * @param context   custom object provided to each {@link graphql.schema.DataFetcher}
     * @param variables variable values uses as argument
     *
     * @return an {@link ExecutionResult} which can include errors
     *
     * @deprecated Use {@link #execute(ExecutionInput)}
     */
    @Deprecated
    public ExecutionResult execute(String query, Object context, Map<String, Object> variables) {
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .context(context)
                .root(context) // This we are doing do be backwards compatible
                .variables(variables)
                .build();
        return execute(executionInput);
    }

    /**
     * Info: This sets context = root to be backwards compatible.
     *
     * @param query         the query/mutation/subscription
     * @param operationName name of the operation to execute
     * @param context       custom object provided to each {@link graphql.schema.DataFetcher}
     * @param variables     variable values uses as argument
     *
     * @return an {@link ExecutionResult} which can include errors
     *
     * @deprecated Use {@link #execute(ExecutionInput)}
     */
    @Deprecated
    public ExecutionResult execute(String query, String operationName, Object context, Map<String, Object> variables) {
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .operationName(operationName)
                .context(context)
                .root(context) // This we are doing do be backwards compatible
                .variables(variables)
                .build();
        return execute(executionInput);
    }

    /**
     * Executes the graphql query using the provided input object builder
     *
     * @param executionInputBuilder {@link ExecutionInput.Builder}
     *
     * @return an {@link ExecutionResult} which can include errors
     */
    public ExecutionResult execute(ExecutionInput.Builder executionInputBuilder) {
        return execute(executionInputBuilder.build());
    }

    /**
     * Executes the graphql query using calling the builder function and giving it a new builder.
     * <p>
     * This allows a lambda style like :
     *
     * <pre>
     * {@code
     *    ExecutionResult result = graphql.execute(input -> input.query("{hello}").root(startingObj).context(contextObj));
     * }
     * </pre>
     *
     * @param builderFunction a function that is given a {@link ExecutionInput.Builder}
     *
     * @return an {@link ExecutionResult} which can include errors
     */
    public ExecutionResult execute(UnaryOperator<ExecutionInput.Builder> builderFunction) {
        return execute(builderFunction.apply(ExecutionInput.newExecutionInput()).build());
    }

    /**
     * Executes the graphql query using the provided input object
     *
     * @param executionInput {@link ExecutionInput}
     *
     * @return an {@link ExecutionResult} which can include errors
     */
    public ExecutionResult execute(ExecutionInput executionInput) {
        return executeAsync(executionInput).join();
    }

    /**
     * Executes the graphql query using the provided input object builder
     * <p>
     * This will return a promise (aka {@link CompletableFuture}) to provide a {@link ExecutionResult}
     * which is the result of executing the provided query.
     *
     * @param executionInputBuilder {@link ExecutionInput.Builder}
     *
     * @return a promise to an {@link ExecutionResult} which can include errors
     */
    public CompletableFuture<ExecutionResult> executeAsync(ExecutionInput.Builder executionInputBuilder) {
        return executeAsync(executionInputBuilder.build());
    }

    /**
     * Executes the graphql query using the provided input object builder
     * <p>
     * This will return a promise (aka {@link CompletableFuture}) to provide a {@link ExecutionResult}
     * which is the result of executing the provided query.
     * <p>
     * This allows a lambda style like :
     *
     * <pre>
     * {@code
     *    ExecutionResult result = graphql.execute(input -> input.query("{hello}").root(startingObj).context(contextObj));
     * }
     * </pre>
     *
     * @param builderFunction a function that is given a {@link ExecutionInput.Builder}
     *
     * @return a promise to an {@link ExecutionResult} which can include errors
     */
    public CompletableFuture<ExecutionResult> executeAsync(UnaryOperator<ExecutionInput.Builder> builderFunction) {
        return executeAsync(builderFunction.apply(ExecutionInput.newExecutionInput()).build());
    }

    /**
     * Executes the graphql query using the provided input object
     * <p>
     * This will return a promise (aka {@link CompletableFuture}) to provide a {@link ExecutionResult}
     * which is the result of executing the provided query.
     *
     * @param executionInput {@link ExecutionInput}
     *
     * @return a promise to an {@link ExecutionResult} which can include errors
     */
    public CompletableFuture<ExecutionResult> executeAsync(ExecutionInput executionInput) {
        log.debug("Executing request. operation name: {}. query: {}. variables {} ", executionInput.getOperationName(), executionInput.getQuery(), executionInput.getVariables());

        InstrumentationState instrumentationState = instrumentation.createState();

        InstrumentationExecutionParameters instrumentationParameters = new InstrumentationExecutionParameters(executionInput, instrumentationState);
        InstrumentationContext<ExecutionResult> executionInstrumentation = instrumentation.beginExecution(instrumentationParameters);
        CompletableFuture<ExecutionResult> executionResult = parseValidateAndExecute(executionInput, instrumentationState);
        //
        // finish up instrumentation
        executionResult = executionResult.thenApply(er -> {
            executionInstrumentation.onEnd(er);
            return er;
        });
        //
        // allow instrumentation to tweak the result
        executionResult = executionResult.thenApply(er -> instrumentation.instrumentExecutionResult(er, instrumentationParameters));
        return executionResult;
    }


    private CompletableFuture<ExecutionResult> parseValidateAndExecute(ExecutionInput executionInput, InstrumentationState instrumentationState) {
        PreparsedDocumentEntry preparsedDoc = preparsedDocumentProvider.get(executionInput.getQuery(), query -> parseAndValidate(executionInput, instrumentationState));

        if (preparsedDoc.hasErrors()) {
            return CompletableFuture.completedFuture(new ExecutionResultImpl(preparsedDoc.getErrors()));
        }

        return execute(executionInput, preparsedDoc.getDocument(), instrumentationState);
    }

    private PreparsedDocumentEntry parseAndValidate(ExecutionInput executionInput, InstrumentationState instrumentationState) {
        ParseResult parseResult = parse(executionInput, instrumentationState);
        if (parseResult.isFailure()) {
            return new PreparsedDocumentEntry(toInvalidSyntaxError(parseResult.getException()));
        } else {
            final Document document = parseResult.getDocument();

            final List<ValidationError> errors = validate(executionInput, document, instrumentationState);
            if (!errors.isEmpty()) {
                return new PreparsedDocumentEntry(errors);
            }

            return new PreparsedDocumentEntry(document);
        }
    }

    private ParseResult parse(ExecutionInput executionInput, InstrumentationState instrumentationState) {
        InstrumentationContext<Document> parseInstrumentation = instrumentation.beginParse(new InstrumentationExecutionParameters(executionInput, instrumentationState));

        Parser parser = new Parser();
        Document document;
        try {
            document = parser.parseDocument(executionInput.getQuery());
        } catch (ParseCancellationException e) {
            parseInstrumentation.onEnd(e);
            return ParseResult.ofError((RecognitionException) e.getCause());
        }

        parseInstrumentation.onEnd(document);
        return ParseResult.of(document);
    }

    private List<ValidationError> validate(ExecutionInput executionInput, Document document, InstrumentationState instrumentationState) {
        InstrumentationContext<List<ValidationError>> validationCtx = instrumentation.beginValidation(new InstrumentationValidationParameters(executionInput, document, instrumentationState));

        Validator validator = new Validator();
        List<ValidationError> validationErrors = validator.validateDocument(graphQLSchema, document);

        validationCtx.onEnd(validationErrors);
        return validationErrors;
    }

    private CompletableFuture<ExecutionResult> execute(ExecutionInput executionInput, Document document, InstrumentationState instrumentationState) {
        String query = executionInput.getQuery();
        String operationName = executionInput.getOperationName();
        Object context = executionInput.getContext();

        Execution execution = new Execution(queryStrategy, mutationStrategy, subscriptionStrategy, instrumentation);
        ExecutionId executionId = idProvider.provide(query, operationName, context);
        return execution.execute(document, graphQLSchema, executionId, executionInput, instrumentationState);
    }

    private InvalidSyntaxError toInvalidSyntaxError(RecognitionException recognitionException) {
        SourceLocation sourceLocation = null;
        if (recognitionException != null) {
            sourceLocation = new SourceLocation(recognitionException.getOffendingToken().getLine(), recognitionException.getOffendingToken().getCharPositionInLine());
        }
        return new InvalidSyntaxError(sourceLocation);
    }

    private static class ParseResult {
        private final Document document;
        private final RecognitionException exception;

        private ParseResult(Document document, RecognitionException exception) {
            this.document = document;
            this.exception = exception;
        }

        private boolean isFailure() {
            return document == null;
        }

        private Document getDocument() {
            return document;
        }

        private RecognitionException getException() {
            return exception;
        }

        private static ParseResult of(Document document) {
            return new ParseResult(document, null);
        }

        private static ParseResult ofError(RecognitionException e) {
            return new ParseResult(null, e);
        }
    }
}
