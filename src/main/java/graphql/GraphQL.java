package graphql;

import graphql.execution.AbortExecutionException;
import graphql.execution.Async;
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
import graphql.execution.instrumentation.DocumentAndVariables;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.execution.preparsed.NoOpPreparsedDocumentProvider;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.introspection.GoodFaithIntrospection;
import graphql.language.Document;
import graphql.schema.GraphQLSchema;
import graphql.validation.GoodFaithIntrospectionExceeded;
import graphql.validation.OperationValidationRule;
import graphql.validation.QueryComplexityLimits;
import graphql.validation.ValidationError;
import graphql.validation.ValidationErrorType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;

import java.util.List;
import java.util.Locale;
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
import static graphql.execution.instrumentation.SimpleInstrumentationContext.nonNullCtx;

/**
 * This class is where all graphql-java query execution begins.  It combines the objects that are needed
 * to make a successful graphql query, with the most important being the {@link graphql.schema.GraphQLSchema schema}
 * and the {@link graphql.execution.ExecutionStrategy execution strategy}
 * <p>
 * Building this object is very cheap and can be done on each execution if necessary.  Building the schema is often not
 * as cheap, especially if it's parsed from graphql IDL schema format via {@link graphql.schema.idl.SchemaParser}.
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
 * object type given an interface or union type.
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
@NullMarked
public class GraphQL {

    /**
     * This allows you to control "unusual" aspects of the GraphQL system
     * including some JVM wide settings
     * <p>
     * This is named unusual because in general we don't expect you to
     * have to make ths configuration by default, but you can opt into certain features
     * or disable them if you want to.
     *
     * @return a {@link GraphQLUnusualConfiguration} object
     */
    public static GraphQLUnusualConfiguration unusualConfiguration() {
        return new GraphQLUnusualConfiguration();
    }

    /**
     * This allows you to control "unusual" per execution aspects of the GraphQL system
     * <p>
     * This is named unusual because in general we don't expect you to
     * have to make ths configuration by default, but you can opt into certain features
     * or disable them if you want to.
     *
     * @return a {@link GraphQLUnusualConfiguration.GraphQLContextConfiguration} object
     */
    public static GraphQLUnusualConfiguration.GraphQLContextConfiguration unusualConfiguration(ExecutionInput executionInput) {
        return new GraphQLUnusualConfiguration.GraphQLContextConfiguration(executionInput.getGraphQLContext());
    }

    /**
     * This allows you to control "unusual" per execution aspects of the GraphQL system
     * <p>
     * This is named unusual because in general we don't expect you to
     * have to make ths configuration by default, but you can opt into certain features
     * or disable them if you want to.
     *
     * @return a {@link GraphQLUnusualConfiguration.GraphQLContextConfiguration} object
     */
    public static GraphQLUnusualConfiguration.GraphQLContextConfiguration unusualConfiguration(ExecutionInput.Builder executionInputBuilder) {
        return new GraphQLUnusualConfiguration.GraphQLContextConfiguration(executionInputBuilder.graphQLContext());
    }

    /**
     * This allows you to control "unusual" per execution aspects of the GraphQL system
     * <p>
     * This is named unusual because in general we don't expect you to
     * have to make ths configuration by default, but you can opt into certain features
     * or disable them if you want to.
     *
     * @return a {@link GraphQLUnusualConfiguration.GraphQLContextConfiguration} object
     */
    public static GraphQLUnusualConfiguration.GraphQLContextConfiguration unusualConfiguration(GraphQLContext graphQLContext) {
        return new GraphQLUnusualConfiguration.GraphQLContextConfiguration(graphQLContext);
    }

    /**
     * This allows you to control "unusual" per execution aspects of the GraphQL system
     * <p>
     * This is named unusual because in general we don't expect you to
     * have to make ths configuration by default, but you can opt into certain features
     * or disable them if you want to.
     *
     * @return a {@link GraphQLUnusualConfiguration.GraphQLContextConfiguration} object
     */
    public static GraphQLUnusualConfiguration.GraphQLContextConfiguration unusualConfiguration(GraphQLContext.Builder graphQLContextBuilder) {
        return new GraphQLUnusualConfiguration.GraphQLContextConfiguration(graphQLContextBuilder);
    }

    private final GraphQLSchema graphQLSchema;
    private final ExecutionStrategy queryStrategy;
    private final ExecutionStrategy mutationStrategy;
    private final ExecutionStrategy subscriptionStrategy;
    private final ExecutionIdProvider idProvider;
    private final Instrumentation instrumentation;
    private final PreparsedDocumentProvider preparsedDocumentProvider;
    private final ValueUnboxer valueUnboxer;
    private final boolean doNotAutomaticallyDispatchDataLoader;


    private GraphQL(Builder builder) {
        this.graphQLSchema = assertNotNull(builder.graphQLSchema, "graphQLSchema must be non null");
        this.queryStrategy = assertNotNull(builder.queryExecutionStrategy, "queryStrategy must not be null");
        this.mutationStrategy = assertNotNull(builder.mutationExecutionStrategy, "mutationStrategy must not be null");
        this.subscriptionStrategy = assertNotNull(builder.subscriptionExecutionStrategy, "subscriptionStrategy must not be null");
        this.idProvider = assertNotNull(builder.idProvider, "idProvider must be non null");
        this.instrumentation = assertNotNull(builder.instrumentation, "instrumentation must not be null");
        this.preparsedDocumentProvider = assertNotNull(builder.preparsedDocumentProvider, "preparsedDocumentProvider must be non null");
        this.valueUnboxer = assertNotNull(builder.valueUnboxer, "valueUnboxer must not be null");
        this.doNotAutomaticallyDispatchDataLoader = builder.doNotAutomaticallyDispatchDataLoader;
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

    public boolean isDoNotAutomaticallyDispatchDataLoader() {
        return doNotAutomaticallyDispatchDataLoader;
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
                .executionIdProvider(this.idProvider)
                .instrumentation(this.instrumentation)
                .preparsedDocumentProvider(this.preparsedDocumentProvider);

        builderConsumer.accept(builder);

        return builder.build();
    }

    @PublicApi
    @NullUnmarked
    public static class Builder {
        private GraphQLSchema graphQLSchema;
        private ExecutionStrategy queryExecutionStrategy;
        private ExecutionStrategy mutationExecutionStrategy;
        private ExecutionStrategy subscriptionExecutionStrategy;
        private DataFetcherExceptionHandler defaultExceptionHandler = new SimpleDataFetcherExceptionHandler();
        private ExecutionIdProvider idProvider = DEFAULT_EXECUTION_ID_PROVIDER;
        private Instrumentation instrumentation = null; // deliberate default here
        private PreparsedDocumentProvider preparsedDocumentProvider = NoOpPreparsedDocumentProvider.INSTANCE;
        private boolean doNotAutomaticallyDispatchDataLoader = false;
        private ValueUnboxer valueUnboxer = ValueUnboxer.DEFAULT;

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

        /**
         * This allows you to set a default {@link graphql.execution.DataFetcherExceptionHandler} that will be used to handle exceptions that happen
         * in {@link graphql.schema.DataFetcher} invocations.
         *
         * @param dataFetcherExceptionHandler the default handler for data fetching exception
         *
         * @return this builder
         */
        public Builder defaultDataFetcherExceptionHandler(DataFetcherExceptionHandler dataFetcherExceptionHandler) {
            this.defaultExceptionHandler = assertNotNull(dataFetcherExceptionHandler, "The DataFetcherExceptionHandler must be non null");
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


        /**
         * Deactivates the automatic dispatching of DataLoaders.
         * If deactivated the user is responsible for dispatching the DataLoaders manually.
         *
         * @return this builder
         */
        public Builder doNotAutomaticallyDispatchDataLoader() {
            this.doNotAutomaticallyDispatchDataLoader = true;
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

            if (instrumentation == null) {
                this.instrumentation = SimplePerformantInstrumentation.INSTANCE;
            }
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
        Profiler profiler = executionInput.isProfileExecution() ? new ProfilerImpl(executionInput.getGraphQLContext()) : Profiler.NO_OP;
        EngineRunningState engineRunningState = new EngineRunningState(executionInput, profiler);
        return engineRunningState.engineRun(() -> {
            ExecutionInput executionInputWithId = ensureInputHasId(executionInput);
            profiler.setExecutionInputAndInstrumentation(executionInputWithId, instrumentation);
            engineRunningState.updateExecutionInput(executionInputWithId);

            CompletableFuture<InstrumentationState> instrumentationStateCF = instrumentation.createStateAsync(new InstrumentationCreateStateParameters(this.graphQLSchema, executionInputWithId));
            instrumentationStateCF = Async.orNullCompletedFuture(instrumentationStateCF);

            return engineRunningState.compose(instrumentationStateCF, (instrumentationState -> {
                try {
                    InstrumentationExecutionParameters inputInstrumentationParameters = new InstrumentationExecutionParameters(executionInputWithId, this.graphQLSchema);
                    ExecutionInput instrumentedExecutionInput = instrumentation.instrumentExecutionInput(executionInputWithId, inputInstrumentationParameters, instrumentationState);

                    InstrumentationExecutionParameters instrumentationParameters = new InstrumentationExecutionParameters(instrumentedExecutionInput, this.graphQLSchema);
                    InstrumentationContext<ExecutionResult> executionInstrumentation = nonNullCtx(instrumentation.beginExecution(instrumentationParameters, instrumentationState));
                    executionInstrumentation.onDispatched();

                    GraphQLSchema graphQLSchema = instrumentation.instrumentSchema(this.graphQLSchema, instrumentationParameters, instrumentationState);

                    CompletableFuture<ExecutionResult> executionResult = parseValidateAndExecute(instrumentedExecutionInput, graphQLSchema, instrumentationState, engineRunningState, profiler);
                    //
                    // finish up instrumentation
                    executionResult = executionResult.whenComplete(completeInstrumentationCtxCF(executionInstrumentation));
                    //
                    // allow instrumentation to tweak the result
                    executionResult = engineRunningState.compose(executionResult, (result -> instrumentation.instrumentExecutionResult(result, instrumentationParameters, instrumentationState)));
                    return executionResult;
                } catch (AbortExecutionException abortException) {
                    return handleAbortException(executionInput, instrumentationState, abortException);
                }
            }));
        });
    }


    private CompletableFuture<ExecutionResult> handleAbortException(ExecutionInput executionInput, InstrumentationState instrumentationState, AbortExecutionException abortException) {
        InstrumentationExecutionParameters instrumentationParameters = new InstrumentationExecutionParameters(executionInput, this.graphQLSchema);
        return instrumentation.instrumentExecutionResult(abortException.toExecutionResult(), instrumentationParameters, instrumentationState);
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


    private CompletableFuture<ExecutionResult> parseValidateAndExecute(ExecutionInput executionInput, GraphQLSchema graphQLSchema, InstrumentationState instrumentationState, EngineRunningState engineRunningState, Profiler profiler) {
        AtomicReference<ExecutionInput> executionInputRef = new AtomicReference<>(executionInput);
        Function<ExecutionInput, PreparsedDocumentEntry> computeFunction = transformedInput -> {
            // if they change the original query in the pre-parser, then we want to see it downstream from then on
            executionInputRef.set(transformedInput);
            return parseAndValidate(executionInputRef, graphQLSchema, instrumentationState);
        };
        CompletableFuture<PreparsedDocumentEntry> preparsedDoc = preparsedDocumentProvider.getDocumentAsync(executionInput, computeFunction);
        return engineRunningState.compose(preparsedDoc, (preparsedDocumentEntry -> {
            if (preparsedDocumentEntry.hasErrors()) {
                return CompletableFuture.completedFuture(new ExecutionResultImpl(preparsedDocumentEntry.getErrors()));
            }
            try {
                return execute(Assert.assertNotNull(executionInputRef.get()), preparsedDocumentEntry.getDocument(), graphQLSchema, instrumentationState, engineRunningState, profiler);
            } catch (AbortExecutionException e) {
                return CompletableFuture.completedFuture(e.toExecutionResult());
            }
        }));
    }

    private PreparsedDocumentEntry parseAndValidate(AtomicReference<ExecutionInput> executionInputRef, GraphQLSchema graphQLSchema, InstrumentationState instrumentationState) {

        ExecutionInput executionInput = assertNotNull(executionInputRef.get());

        ParseAndValidateResult parseResult = parse(executionInput, graphQLSchema, instrumentationState);
        if (parseResult.isFailure()) {
            return new PreparsedDocumentEntry(parseResult.getSyntaxException().toInvalidSyntaxError());
        } else {
            final Document document = parseResult.getDocument();
            // they may have changed the document and the variables via instrumentation so update the reference to it
            executionInput = executionInput.transform(builder -> builder.variables(parseResult.getVariables()));
            executionInputRef.set(executionInput);

            final List<ValidationError> errors;
            try {
                errors = validate(executionInput, document, graphQLSchema, instrumentationState);
            } catch (GoodFaithIntrospectionExceeded e) {
                return new PreparsedDocumentEntry(document, List.of(e.toBadFaithError()));
            }
            if (!errors.isEmpty()) {
                return new PreparsedDocumentEntry(document, errors);
            }

            return new PreparsedDocumentEntry(document);
        }
    }

    private ParseAndValidateResult parse(ExecutionInput executionInput, GraphQLSchema graphQLSchema, InstrumentationState instrumentationState) {
        InstrumentationExecutionParameters parameters = new InstrumentationExecutionParameters(executionInput, graphQLSchema);
        InstrumentationContext<Document> parseInstrumentationCtx = nonNullCtx(instrumentation.beginParse(parameters, instrumentationState));
        parseInstrumentationCtx.onDispatched();

        ParseAndValidateResult parseResult = ParseAndValidate.parse(executionInput);
        if (parseResult.isFailure()) {
            parseInstrumentationCtx.onCompleted(null, parseResult.getSyntaxException());
            return parseResult;
        } else {
            parseInstrumentationCtx.onCompleted(parseResult.getDocument(), null);

            DocumentAndVariables documentAndVariables = parseResult.getDocumentAndVariables();
            documentAndVariables = instrumentation.instrumentDocumentAndVariables(documentAndVariables, parameters, instrumentationState);
            return ParseAndValidateResult.newResult()
                    .document(documentAndVariables.getDocument()).variables(documentAndVariables.getVariables()).build();
        }
    }

    private List<ValidationError> validate(ExecutionInput executionInput, Document document, GraphQLSchema graphQLSchema, InstrumentationState instrumentationState) {
        InstrumentationContext<List<ValidationError>> validationCtx = nonNullCtx(instrumentation.beginValidation(new InstrumentationValidationParameters(executionInput, document, graphQLSchema), instrumentationState));
        validationCtx.onDispatched();

        Predicate<OperationValidationRule> validationRulePredicate = executionInput.getGraphQLContext().getOrDefault(ParseAndValidate.INTERNAL_VALIDATION_PREDICATE_HINT, r -> true);
        Locale locale = executionInput.getLocale() != null ? executionInput.getLocale() : Locale.getDefault();
        QueryComplexityLimits limits = executionInput.getGraphQLContext().get(QueryComplexityLimits.KEY);

        // Good Faith Introspection: apply tighter limits and enable the rule for introspection queries
        boolean goodFaithActive = GoodFaithIntrospection.isEnabled(executionInput.getGraphQLContext())
                && GoodFaithIntrospection.containsIntrospectionFields(document);
        if (goodFaithActive) {
            limits = GoodFaithIntrospection.goodFaithLimits(limits);
        } else {
            Predicate<OperationValidationRule> existing = validationRulePredicate;
            validationRulePredicate = rule -> rule != OperationValidationRule.GOOD_FAITH_INTROSPECTION && existing.test(rule);
        }

        List<ValidationError> validationErrors = ParseAndValidate.validate(graphQLSchema, document, validationRulePredicate, locale, limits);

        // If good faith is active and a complexity limit error was produced, convert it to a bad faith error
        if (goodFaithActive) {
            for (ValidationError error : validationErrors) {
                if (error.getValidationErrorType() == ValidationErrorType.MaxQueryFieldsExceeded
                        || error.getValidationErrorType() == ValidationErrorType.MaxQueryDepthExceeded) {
                    validationCtx.onCompleted(null, null);
                    throw GoodFaithIntrospectionExceeded.tooBigOperation(error.getDescription());
                }
            }
        }

        validationCtx.onCompleted(validationErrors, null);
        return validationErrors;
    }

    private CompletableFuture<ExecutionResult> execute(ExecutionInput executionInput,
                                                       Document document,
                                                       GraphQLSchema graphQLSchema,
                                                       InstrumentationState instrumentationState,
                                                       EngineRunningState engineRunningState,
                                                       Profiler profiler
    ) {

        Execution execution = new Execution(queryStrategy, mutationStrategy, subscriptionStrategy, instrumentation, valueUnboxer, doNotAutomaticallyDispatchDataLoader);
        ExecutionId executionId = executionInput.getExecutionId();

        return execution.execute(document, graphQLSchema, executionId, executionInput, instrumentationState, engineRunningState, profiler);
    }

}
