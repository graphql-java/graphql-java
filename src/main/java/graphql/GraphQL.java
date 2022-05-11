package graphql;

import graphql.execution.AbortExecutionException;
import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.AsyncSerialExecutionStrategy;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.Execution;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionIdProvider;
import graphql.execution.ExecutionStrategy;
import graphql.execution.SimpleDataFetcherExceptionHandler;
import graphql.execution.SubscriptionExecutionStrategy;
import graphql.execution.ValueUnboxer;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.DocumentAndVariables;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.execution.preparsed.NoOpPreparsedDocumentProvider;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.language.Document;
import graphql.schema.GraphQLSchema;
import graphql.util.LogKit;
import graphql.validation.ValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static graphql.Assert.assertNotNull;
import static graphql.execution.ExecutionIdProvider.DEFAULT_EXECUTION_ID_PROVIDER;
import static graphql.execution.instrumentation.SimpleInstrumentationContext.completeInstrumentationCtxCF;

/**
 * This class is where all graphql-java query execution begins.  It combines the objects that are needed
 * to make a successful graphql query, with the most important being the {@link graphql.schema.GraphQLSchema schema}
 * and the {@link graphql.execution.ExecutionStrategy execution strategy}
 * <p>
 * Building this object is very cheap and can be done on each execution if necessary.  Building the schema is often not
 * as cheap, especially if its parsed from graphql IDL schema format via {@link graphql.schema.idl.SchemaParser}.
 * <p>
 * The data for a query is returned via {@link ExecutionResult#getData()} and any errors encountered as placed in
 * {@link ExecutionResult#getErrors()}.
 *
 * <h2>Runtime Exceptions</h2>
 * <p>
 * Runtime exceptions can be thrown by the graphql engine if certain situations are encountered.  These are not errors
 * in execution but rather totally unacceptable conditions in which to execute a graphql query.
 * <ul>
 * <li>{@link graphql.schema.CoercingSerializeException} - is thrown when a value cannot be serialised by a Scalar type, for example
 * a String value being coerced as an Int.
 * </li>
 *
 * <li>{@link graphql.execution.UnresolvedTypeException} - is thrown if a {@link graphql.schema.TypeResolver} fails to provide a concrete
 * object type given a interface or union type.
 * </li>
 *
 * <li>{@link graphql.schema.validation.InvalidSchemaException} - is thrown if the schema is not valid when built via
 * {@link graphql.schema.GraphQLSchema.Builder#build()}
 * </li>
 *
 * <li>{@link graphql.GraphQLException} - is thrown as a general purpose runtime exception, for example if the code cant
 * access a named field when examining a POJO.
 * </li>
 *
 * <li>{@link graphql.AssertException} - is thrown as a low level code assertion exception for truly unexpected code conditions
 * </li>
 *
 * </ul>
 */
@SuppressWarnings("Duplicates")
@PublicApi
public class GraphQL {

    private static final Logger log = LoggerFactory.getLogger(GraphQL.class);
    private static final Logger logNotSafe = LogKit.getNotPrivacySafeLogger(GraphQL.class);

    private final GraphQLSchema graphQLSchema;
    private final ExecutionStrategy queryStrategy;
    private final ExecutionStrategy mutationStrategy;
    private final ExecutionStrategy subscriptionStrategy;
    private final ExecutionIdProvider idProvider;
    private final Instrumentation instrumentation;
    private final PreparsedDocumentProvider preparsedDocumentProvider;
    private final ValueUnboxer valueUnboxer;


    private GraphQL(Builder builder) {
        this.graphQLSchema = assertNotNull(builder.graphQLSchema, () -> "graphQLSchema must be non null");
        this.queryStrategy = assertNotNull(builder.queryExecutionStrategy, () -> "queryStrategy must not be null");
        this.mutationStrategy = assertNotNull(builder.mutationExecutionStrategy, () -> "mutationStrategy must not be null");
        this.subscriptionStrategy = assertNotNull(builder.subscriptionExecutionStrategy, () -> "subscriptionStrategy must not be null");
        this.idProvider = assertNotNull(builder.idProvider, () -> "idProvider must be non null");
        this.instrumentation = assertNotNull(builder.instrumentation, () -> "instrumentation must not be null");
        this.preparsedDocumentProvider = assertNotNull(builder.preparsedDocumentProvider, () -> "preparsedDocumentProvider must be non null");
        this.valueUnboxer = assertNotNull(builder.valueUnboxer, () -> "valueUnboxer must not be null");
    }

    /**
     * @return the schema backing this {@link GraphQL} instance
     */
    public GraphQLSchema getGraphQLSchema() {
        return graphQLSchema;
    }

    /**
     * @return the execution strategy used for queries in this {@link GraphQL} instance
     */
    public ExecutionStrategy getQueryStrategy() {
        return queryStrategy;
    }

    /**
     * @return the execution strategy used for mutation in this {@link GraphQL} instance
     */
    public ExecutionStrategy getMutationStrategy() {
        return mutationStrategy;
    }

    /**
     * @return the execution strategy used for subscriptions in this {@link GraphQL} instance
     */
    public ExecutionStrategy getSubscriptionStrategy() {
        return subscriptionStrategy;
    }

    /**
     * @return the provider of execution ids for this {@link GraphQL} instance
     */
    public ExecutionIdProvider getIdProvider() {
        return idProvider;
    }

    /**
     * @return the Instrumentation for this {@link GraphQL} instance, if any
     */
    public Instrumentation getInstrumentation() {
        return instrumentation;
    }

    /**
     * @return the PreparsedDocumentProvider for this {@link GraphQL} instance
     */
    public PreparsedDocumentProvider getPreparsedDocumentProvider() {
        return preparsedDocumentProvider;
    }

    /**
     * @return the ValueUnboxer for this {@link GraphQL} instance
     */
    public ValueUnboxer getValueUnboxer() {
        return valueUnboxer;
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

    /**
     * This helps you transform the current GraphQL object into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new GraphQL object based on calling build on that builder
     */
    public GraphQL transform(Consumer<GraphQL.Builder> builderConsumer) {
        Builder builder = new Builder(this.graphQLSchema);
        builder
                .queryExecutionStrategy(this.queryStrategy)
                .mutationExecutionStrategy(this.mutationStrategy)
                .subscriptionExecutionStrategy(this.subscriptionStrategy)
                .executionIdProvider(Optional.ofNullable(this.idProvider).orElse(builder.idProvider))
                .instrumentation(Optional.ofNullable(this.instrumentation).orElse(builder.instrumentation))
                .preparsedDocumentProvider(Optional.ofNullable(this.preparsedDocumentProvider).orElse(builder.preparsedDocumentProvider));

        builderConsumer.accept(builder);

        return builder.build();
    }

    @PublicApi
    public static class Builder {
        private GraphQLSchema graphQLSchema;
        private ExecutionStrategy queryExecutionStrategy;
        private ExecutionStrategy mutationExecutionStrategy;
        private ExecutionStrategy subscriptionExecutionStrategy;
        private DataFetcherExceptionHandler defaultExceptionHandler = new SimpleDataFetcherExceptionHandler();
        private ExecutionIdProvider idProvider = DEFAULT_EXECUTION_ID_PROVIDER;
        private Instrumentation instrumentation = null; // deliberate default here
        private PreparsedDocumentProvider preparsedDocumentProvider = NoOpPreparsedDocumentProvider.INSTANCE;
        private boolean doNotAddDefaultInstrumentations = false;
        private ValueUnboxer valueUnboxer = ValueUnboxer.DEFAULT;


        public Builder(GraphQLSchema graphQLSchema) {
            this.graphQLSchema = graphQLSchema;
        }

        public Builder schema(GraphQLSchema graphQLSchema) {
            this.graphQLSchema = assertNotNull(graphQLSchema, () -> "GraphQLSchema must be non null");
            return this;
        }

        public Builder queryExecutionStrategy(ExecutionStrategy executionStrategy) {
            this.queryExecutionStrategy = assertNotNull(executionStrategy, () -> "Query ExecutionStrategy must be non null");
            return this;
        }

        public Builder mutationExecutionStrategy(ExecutionStrategy executionStrategy) {
            this.mutationExecutionStrategy = assertNotNull(executionStrategy, () -> "Mutation ExecutionStrategy must be non null");
            return this;
        }

        public Builder subscriptionExecutionStrategy(ExecutionStrategy executionStrategy) {
            this.subscriptionExecutionStrategy = assertNotNull(executionStrategy, () -> "Subscription ExecutionStrategy must be non null");
            return this;
        }

        /**
         * This allows you to set a default {@link graphql.execution.DataFetcherExceptionHandler} that will be used to handle exceptions that happen
         * in {@link graphql.schema.DataFetcher} invocations.
         *
         * @param dataFetcherExceptionHandler the default handler for data fetching exception
         *
         * @return this builder
         */
        public Builder defaultDataFetcherExceptionHandler(DataFetcherExceptionHandler dataFetcherExceptionHandler) {
            this.defaultExceptionHandler = assertNotNull(dataFetcherExceptionHandler, () -> "The DataFetcherExceptionHandler must be non null");
            return this;
        }

        public Builder instrumentation(Instrumentation instrumentation) {
            this.instrumentation = assertNotNull(instrumentation, () -> "Instrumentation must be non null");
            return this;
        }

        public Builder preparsedDocumentProvider(PreparsedDocumentProvider preparsedDocumentProvider) {
            this.preparsedDocumentProvider = assertNotNull(preparsedDocumentProvider, () -> "PreparsedDocumentProvider must be non null");
            return this;
        }

        public Builder executionIdProvider(ExecutionIdProvider executionIdProvider) {
            this.idProvider = assertNotNull(executionIdProvider, () -> "ExecutionIdProvider must be non null");
            return this;
        }

        /**
         * For performance reasons you can opt into situation where the default instrumentations (such
         * as {@link graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation} will not be
         * automatically added into the graphql instance.
         * <p>
         * For most situations this is not needed unless you are really pushing the boundaries of performance
         * <p>
         * By default a certain graphql instrumentations will be added to the mix to more easily enable certain functionality.  This
         * allows you to stop this behavior
         *
         * @return this builder
         */
        public Builder doNotAddDefaultInstrumentations() {
            this.doNotAddDefaultInstrumentations = true;
            return this;
        }

        public Builder valueUnboxer(ValueUnboxer valueUnboxer) {
            this.valueUnboxer = valueUnboxer;
            return this;
        }

        public GraphQL build() {
            // we use the data fetcher exception handler unless they set their own strategy in which case bets are off
            if (queryExecutionStrategy == null) {
                this.queryExecutionStrategy = new AsyncExecutionStrategy(this.defaultExceptionHandler);
            }
            if (mutationExecutionStrategy == null) {
                this.mutationExecutionStrategy = new AsyncSerialExecutionStrategy(this.defaultExceptionHandler);
            }
            if (subscriptionExecutionStrategy == null) {
                this.subscriptionExecutionStrategy = new SubscriptionExecutionStrategy(this.defaultExceptionHandler);
            }

            this.instrumentation = checkInstrumentationDefaultState(this.instrumentation, this.doNotAddDefaultInstrumentations);
            return new GraphQL(this);
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
        try {
            return executeAsync(executionInput).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            } else {
                throw e;
            }
        }
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
        try {
            if (logNotSafe.isDebugEnabled()) {
                logNotSafe.debug("Executing request. operation name: '{}'. query: '{}'. variables '{}'", executionInput.getOperationName(), executionInput.getQuery(), executionInput.getVariables());
            }
            executionInput = ensureInputHasId(executionInput);

            InstrumentationState instrumentationState = instrumentation.createState(new InstrumentationCreateStateParameters(this.graphQLSchema, executionInput));

            InstrumentationExecutionParameters inputInstrumentationParameters = new InstrumentationExecutionParameters(executionInput, this.graphQLSchema, instrumentationState);
            executionInput = instrumentation.instrumentExecutionInput(executionInput, inputInstrumentationParameters);

            CompletableFuture<ExecutionResult> beginExecutionCF = new CompletableFuture<>();
            InstrumentationExecutionParameters instrumentationParameters = new InstrumentationExecutionParameters(executionInput, this.graphQLSchema, instrumentationState);
            InstrumentationContext<ExecutionResult> executionInstrumentation = instrumentation.beginExecution(instrumentationParameters);
            executionInstrumentation.onDispatched(beginExecutionCF);

            GraphQLSchema graphQLSchema = instrumentation.instrumentSchema(this.graphQLSchema, instrumentationParameters);

            CompletableFuture<ExecutionResult> executionResult = parseValidateAndExecute(executionInput, graphQLSchema, instrumentationState);
            //
            // finish up instrumentation
            executionResult = executionResult.whenComplete(completeInstrumentationCtxCF(executionInstrumentation, beginExecutionCF));
            //
            // allow instrumentation to tweak the result
            executionResult = executionResult.thenCompose(result -> instrumentation.instrumentExecutionResult(result, instrumentationParameters));
            return executionResult;
        } catch (AbortExecutionException abortException) {
            return CompletableFuture.completedFuture(abortException.toExecutionResult());
        }
    }

    private ExecutionInput ensureInputHasId(ExecutionInput executionInput) {
        if (executionInput.getExecutionId() != null) {
            return executionInput;
        }
        String queryString = executionInput.getQuery();
        String operationName = executionInput.getOperationName();
        Object context = executionInput.getGraphQLContext();
        return executionInput.transform(builder -> builder.executionId(idProvider.provide(queryString, operationName, context)));
    }


    private CompletableFuture<ExecutionResult> parseValidateAndExecute(ExecutionInput executionInput, GraphQLSchema graphQLSchema, InstrumentationState instrumentationState) {
        AtomicReference<ExecutionInput> executionInputRef = new AtomicReference<>(executionInput);
        Function<ExecutionInput, PreparsedDocumentEntry> computeFunction = transformedInput -> {
            // if they change the original query in the pre-parser, then we want to see it downstream from then on
            executionInputRef.set(transformedInput);
            return parseAndValidate(executionInputRef, graphQLSchema, instrumentationState);
        };
        CompletableFuture<PreparsedDocumentEntry> preparsedDoc = preparsedDocumentProvider.getDocumentAsync(executionInput, computeFunction);
        return preparsedDoc.thenCompose(preparsedDocumentEntry -> {
            if (preparsedDocumentEntry.hasErrors()) {
                return CompletableFuture.completedFuture(new ExecutionResultImpl(preparsedDocumentEntry.getErrors()));
            }
            try {
                return execute(executionInputRef.get(), preparsedDocumentEntry.getDocument(), graphQLSchema, instrumentationState);
            } catch (AbortExecutionException e) {
                return CompletableFuture.completedFuture(e.toExecutionResult());
            }
        });
    }

    private PreparsedDocumentEntry parseAndValidate(AtomicReference<ExecutionInput> executionInputRef, GraphQLSchema graphQLSchema, InstrumentationState instrumentationState) {

        ExecutionInput executionInput = executionInputRef.get();
        String query = executionInput.getQuery();

        if (logNotSafe.isDebugEnabled()) {
            logNotSafe.debug("Parsing query: '{}'...", query);
        }
        ParseAndValidateResult parseResult = parse(executionInput, graphQLSchema, instrumentationState);
        if (parseResult.isFailure()) {
            logNotSafe.warn("Query did not parse : '{}'", executionInput.getQuery());
            return new PreparsedDocumentEntry(parseResult.getSyntaxException().toInvalidSyntaxError());
        } else {
            final Document document = parseResult.getDocument();
            // they may have changed the document and the variables via instrumentation so update the reference to it
            executionInput = executionInput.transform(builder -> builder.variables(parseResult.getVariables()));
            executionInputRef.set(executionInput);

            if (logNotSafe.isDebugEnabled()) {
                logNotSafe.debug("Validating query: '{}'", query);
            }
            final List<ValidationError> errors = validate(executionInput, document, graphQLSchema, instrumentationState);
            if (!errors.isEmpty()) {
                logNotSafe.warn("Query did not validate : '{}'", query);
                return new PreparsedDocumentEntry(errors);
            }

            return new PreparsedDocumentEntry(document);
        }
    }

    private ParseAndValidateResult parse(ExecutionInput executionInput, GraphQLSchema graphQLSchema, InstrumentationState instrumentationState) {
        InstrumentationExecutionParameters parameters = new InstrumentationExecutionParameters(executionInput, graphQLSchema, instrumentationState);
        InstrumentationContext<Document> parseInstrumentation = instrumentation.beginParse(parameters);
        CompletableFuture<Document> documentCF = new CompletableFuture<>();
        parseInstrumentation.onDispatched(documentCF);

        ParseAndValidateResult parseResult = ParseAndValidate.parse(executionInput);
        if (parseResult.isFailure()) {
            parseInstrumentation.onCompleted(null, parseResult.getSyntaxException());
            return parseResult;
        } else {
            documentCF.complete(parseResult.getDocument());
            parseInstrumentation.onCompleted(parseResult.getDocument(), null);

            DocumentAndVariables documentAndVariables = parseResult.getDocumentAndVariables();
            documentAndVariables = instrumentation.instrumentDocumentAndVariables(documentAndVariables, parameters);
            return ParseAndValidateResult.newResult()
                    .document(documentAndVariables.getDocument()).variables(documentAndVariables.getVariables()).build();
        }
    }

    private List<ValidationError> validate(ExecutionInput executionInput, Document document, GraphQLSchema graphQLSchema, InstrumentationState instrumentationState) {
        InstrumentationContext<List<ValidationError>> validationCtx = instrumentation.beginValidation(new InstrumentationValidationParameters(executionInput, document, graphQLSchema, instrumentationState));
        CompletableFuture<List<ValidationError>> cf = new CompletableFuture<>();
        validationCtx.onDispatched(cf);

        Predicate<Class<?>> validationRulePredicate = executionInput.getGraphQLContext().getOrDefault(ParseAndValidate.INTERNAL_VALIDATION_PREDICATE_HINT, r -> true);
        List<ValidationError> validationErrors = ParseAndValidate.validate(graphQLSchema, document, validationRulePredicate);

        validationCtx.onCompleted(validationErrors, null);
        cf.complete(validationErrors);
        return validationErrors;
    }

    private CompletableFuture<ExecutionResult> execute(ExecutionInput executionInput, Document document, GraphQLSchema graphQLSchema, InstrumentationState instrumentationState) {

        Execution execution = new Execution(queryStrategy, mutationStrategy, subscriptionStrategy, instrumentation, valueUnboxer);
        ExecutionId executionId = executionInput.getExecutionId();

        if (logNotSafe.isDebugEnabled()) {
            logNotSafe.debug("Executing '{}'. operation name: '{}'. query: '{}'. variables '{}'", executionId, executionInput.getOperationName(), executionInput.getQuery(), executionInput.getVariables());
        }
        CompletableFuture<ExecutionResult> future = execution.execute(document, graphQLSchema, executionId, executionInput, instrumentationState);
        future = future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                logNotSafe.error(String.format("Execution '%s' threw exception when executing : query : '%s'. variables '%s'", executionId, executionInput.getQuery(), executionInput.getVariables()), throwable);
            } else {
                if (log.isDebugEnabled()) {
                    int errorCount = result.getErrors().size();
                    if (errorCount > 0) {
                        log.debug("Execution '{}' completed with '{}' errors", executionId, errorCount);
                    } else {
                        log.debug("Execution '{}' completed with zero errors", executionId);
                    }
                }
            }
        });
        return future;
    }

    private static Instrumentation checkInstrumentationDefaultState(Instrumentation instrumentation, boolean doNotAddDefaultInstrumentations) {
        if (doNotAddDefaultInstrumentations) {
            return instrumentation == null ? SimpleInstrumentation.INSTANCE : instrumentation;
        }
        if (instrumentation instanceof DataLoaderDispatcherInstrumentation) {
            return instrumentation;
        }
        if (instrumentation == null) {
            return new DataLoaderDispatcherInstrumentation();
        }

        //
        // if we don't have a DataLoaderDispatcherInstrumentation in play, we add one.  We want DataLoader to be 1st class in graphql without requiring
        // people to remember to wire it in.  Later we may decide to have more default instrumentations but for now its just the one
        //
        List<Instrumentation> instrumentationList = new ArrayList<>();
        if (instrumentation instanceof ChainedInstrumentation) {
            instrumentationList.addAll(((ChainedInstrumentation) instrumentation).getInstrumentations());
        } else {
            instrumentationList.add(instrumentation);
        }
        boolean containsDLInstrumentation = instrumentationList.stream().anyMatch(instr -> instr instanceof DataLoaderDispatcherInstrumentation);
        if (!containsDLInstrumentation) {
            instrumentationList.add(new DataLoaderDispatcherInstrumentation());
        }
        return new ChainedInstrumentation(instrumentationList);
    }
}