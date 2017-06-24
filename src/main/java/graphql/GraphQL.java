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
        this.queryStrategy = queryStrategy != null ? queryStrategy : new SimpleExecutionStrategy();
        this.mutationStrategy = mutationStrategy != null ? mutationStrategy : new SimpleExecutionStrategy();
        this.subscriptionStrategy = subscriptionStrategy != null ? subscriptionStrategy : new SimpleExecutionStrategy();
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
        private ExecutionStrategy queryExecutionStrategy = new SimpleExecutionStrategy();
        private ExecutionStrategy mutationExecutionStrategy = new SimpleExecutionStrategy();
        private ExecutionStrategy subscriptionExecutionStrategy = new SimpleExecutionStrategy();
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
     * @param query the query/mutation/subscription
     *
     * @return result including errors
     *
     * @deprecated Use {@link #execute(ExecutionInput)}
     */
    @Deprecated
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
     * @return result including errors
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
     * @return result including errors
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
     * @return result including errors
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
     * @return result including errors
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
     * @param executionInput {@link ExecutionInput}
     *
     * @return result including errors
     */
    public ExecutionResult execute(ExecutionInput executionInput) {
        log.debug("Executing request. operation name: {}. query: {}. variables {} ", executionInput.getOperationName(), executionInput.getQuery(), executionInput.getVariables());

        InstrumentationContext<ExecutionResult> executionInstrumentation = instrumentation.beginExecution(new InstrumentationExecutionParameters(executionInput));
        final ExecutionResult executionResult = parseValidateAndExecute(executionInput);
        executionInstrumentation.onEnd(executionResult);

        return executionResult;
    }

    private ExecutionResult parseValidateAndExecute(ExecutionInput executionInput) {
        PreparsedDocumentEntry preparsedDoc = preparsedDocumentProvider.get(executionInput.getQuery(), query -> {
            ParseResult parseResult = parse(executionInput);
            if (parseResult.isFailure()) {
                return new PreparsedDocumentEntry(toInvalidSyntaxError(parseResult.getException()));
            } else {
                final Document document = parseResult.getDocument();

                final List<ValidationError> errors = validate(executionInput, document);
                if (!errors.isEmpty()) {
                    return new PreparsedDocumentEntry(errors);
                }

                return  new PreparsedDocumentEntry(document);
            }
        });

        if (preparsedDoc.hasErrors()) {
            return new ExecutionResultImpl(preparsedDoc.getErrors());
        }

        return execute(executionInput, preparsedDoc.getDocument());
    }

    private ParseResult parse(ExecutionInput executionInput) {
        InstrumentationContext<Document> parseInstrumentation = instrumentation.beginParse(new InstrumentationExecutionParameters(executionInput));

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

    private List<ValidationError> validate(ExecutionInput executionInput, Document document) {
        InstrumentationContext<List<ValidationError>> validationCtx = instrumentation.beginValidation(new InstrumentationValidationParameters(executionInput, document));

        Validator validator = new Validator();
        List<ValidationError> validationErrors = validator.validateDocument(graphQLSchema, document);

        validationCtx.onEnd(validationErrors);
        return validationErrors;
    }

    private ExecutionResult execute(ExecutionInput executionInput, Document document) {
        String query = executionInput.getQuery();
        String operationName = executionInput.getOperationName();
        Object context = executionInput.getContext();

        Execution execution = new Execution(queryStrategy, mutationStrategy, subscriptionStrategy, instrumentation);
        ExecutionId executionId = idProvider.provide(query, operationName, context);
        return execution.execute(document, graphQLSchema, executionId, executionInput);
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
