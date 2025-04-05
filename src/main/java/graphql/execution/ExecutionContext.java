package graphql.execution;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.ExecutionInput;
import graphql.ExperimentalApi;
import graphql.GraphQLContext;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.execution.EngineRunningObserver.RunningState;
import graphql.execution.incremental.IncrementalCallState;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.normalized.ExecutableNormalizedOperation;
import graphql.normalized.ExecutableNormalizedOperationFactory;
import graphql.schema.GraphQLSchema;
import graphql.util.FpKit;
import graphql.util.LockKit;
import org.dataloader.DataLoaderRegistry;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static graphql.Assert.assertTrue;
import static graphql.execution.EngineRunningObserver.RunningState.CANCELLED;
import static graphql.execution.EngineRunningObserver.RunningState.NOT_RUNNING;
import static graphql.execution.EngineRunningObserver.RunningState.RUNNING;

@SuppressWarnings("TypeParameterUnusedInFormals")
@PublicApi
public class ExecutionContext {

    private final GraphQLSchema graphQLSchema;
    private final ExecutionId executionId;
    private final InstrumentationState instrumentationState;
    private final ExecutionStrategy queryStrategy;
    private final ExecutionStrategy mutationStrategy;
    private final ExecutionStrategy subscriptionStrategy;
    private final ImmutableMap<String, FragmentDefinition> fragmentsByName;
    private final OperationDefinition operationDefinition;
    private final Document document;
    private final CoercedVariables coercedVariables;
    private final Supplier<NormalizedVariables> normalizedVariables;
    private final Object root;
    private final Object context;
    private final GraphQLContext graphQLContext;
    private final Object localContext;
    private final Instrumentation instrumentation;
    private final AtomicReference<ImmutableList<GraphQLError>> errors = new AtomicReference<>(ImmutableKit.emptyList());
    private final LockKit.ReentrantLock errorsLock = new LockKit.ReentrantLock();
    private final Set<ResultPath> errorPaths = new HashSet<>();
    private final DataLoaderRegistry dataLoaderRegistry;
    private final Locale locale;
    private final IncrementalCallState incrementalCallState = new IncrementalCallState();
    private final ValueUnboxer valueUnboxer;
    private final ExecutionInput executionInput;
    private final Supplier<ExecutableNormalizedOperation> queryTree;
    private final boolean propagateErrorsOnNonNullContractFailure;

    private final AtomicInteger isRunning = new AtomicInteger(0);

    // this is modified after creation so it needs to be volatile to ensure visibility across Threads
    private volatile DataLoaderDispatchStrategy dataLoaderDispatcherStrategy = DataLoaderDispatchStrategy.NO_OP;

    private final ResultNodesInfo resultNodesInfo = new ResultNodesInfo();
    private final EngineRunningObserver engineRunningObserver;

    ExecutionContext(ExecutionContextBuilder builder) {
        this.graphQLSchema = builder.graphQLSchema;
        this.executionId = builder.executionId;
        this.instrumentationState = builder.instrumentationState;
        this.queryStrategy = builder.queryStrategy;
        this.mutationStrategy = builder.mutationStrategy;
        this.subscriptionStrategy = builder.subscriptionStrategy;
        this.fragmentsByName = builder.fragmentsByName;
        this.coercedVariables = builder.coercedVariables;
        this.normalizedVariables = builder.normalizedVariables;
        this.document = builder.document;
        this.operationDefinition = builder.operationDefinition;
        this.context = builder.context;
        this.graphQLContext = builder.graphQLContext;
        this.root = builder.root;
        this.instrumentation = builder.instrumentation;
        this.dataLoaderRegistry = builder.dataLoaderRegistry;
        this.locale = builder.locale;
        this.valueUnboxer = builder.valueUnboxer;
        this.errors.set(builder.errors);
        this.localContext = builder.localContext;
        this.executionInput = builder.executionInput;
        this.dataLoaderDispatcherStrategy = builder.dataLoaderDispatcherStrategy;
        this.queryTree = FpKit.interThreadMemoize(() -> ExecutableNormalizedOperationFactory.createExecutableNormalizedOperation(graphQLSchema, operationDefinition, fragmentsByName, coercedVariables));
        this.propagateErrorsOnNonNullContractFailure = builder.propagateErrorsOnNonNullContractFailure;
        this.engineRunningObserver = builder.engineRunningObserver;
    }


    public ExecutionId getExecutionId() {
        return executionId;
    }

    public ExecutionInput getExecutionInput() {
        return executionInput;
    }

    public InstrumentationState getInstrumentationState() {
        return instrumentationState;
    }

    public Instrumentation getInstrumentation() {
        return instrumentation;
    }

    public GraphQLSchema getGraphQLSchema() {
        return graphQLSchema;
    }

    public Map<String, FragmentDefinition> getFragmentsByName() {
        return fragmentsByName;
    }

    public Document getDocument() {
        return document;
    }

    public OperationDefinition getOperationDefinition() {
        return operationDefinition;
    }

    public CoercedVariables getCoercedVariables() {
        return coercedVariables;
    }

    /**
     * @return a supplier that will give out the operations variables in normalized form
     */
    public Supplier<NormalizedVariables> getNormalizedVariables() {
        return normalizedVariables;
    }

    /**
     * @param <T> for two
     *
     * @return the legacy context
     *
     * @deprecated use {@link #getGraphQLContext()} instead
     */
    @Deprecated(since = "2021-07-05")
    @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
    public <T> T getContext() {
        return (T) context;
    }

    public GraphQLContext getGraphQLContext() {
        return graphQLContext;
    }

    @SuppressWarnings("unchecked")
    public <T> T getLocalContext() {
        return (T) localContext;
    }

    @SuppressWarnings("unchecked")
    public <T> T getRoot() {
        return (T) root;
    }

    public FragmentDefinition getFragment(String name) {
        return fragmentsByName.get(name);
    }

    public DataLoaderRegistry getDataLoaderRegistry() {
        return dataLoaderRegistry;
    }

    public Locale getLocale() {
        return locale;
    }

    public ValueUnboxer getValueUnboxer() {
        return valueUnboxer;
    }

    /**
     * @return true if the current operation should propagate errors in non-null positions
     * Propagating errors is the default. Error aware clients may opt in returning null in non-null positions
     * by using the `@experimental_disableErrorPropagation` directive.
     *
     * @see graphql.Directives#setExperimentalDisableErrorPropagationEnabled(boolean) to change the JVM wide default
     */
    @ExperimentalApi
    public boolean propagateErrorsOnNonNullContractFailure() {
        return propagateErrorsOnNonNullContractFailure;
    }

    /**
     * @return true if the current operation is a Query
     */
    public boolean isQueryOperation() {
        return isOpType(OperationDefinition.Operation.QUERY);
    }

    /**
     * @return true if the current operation is a Mutation
     */
    public boolean isMutationOperation() {
        return isOpType(OperationDefinition.Operation.MUTATION);
    }

    /**
     * @return true if the current operation is a Subscription
     */
    public boolean isSubscriptionOperation() {
        return isOpType(OperationDefinition.Operation.SUBSCRIPTION);
    }

    private boolean isOpType(OperationDefinition.Operation operation) {
        if (operationDefinition != null) {
            return operation.equals(operationDefinition.getOperation());
        }
        return false;
    }

    /**
     * This method will only put one error per field path.
     *
     * @param error     the error to add
     * @param fieldPath the field path to put it under
     */
    public void addError(GraphQLError error, ResultPath fieldPath) {
        errorsLock.runLocked(() -> {
            //
            // see https://spec.graphql.org/October2021/#sec-Handling-Field-Errors about how per
            // field errors should be handled - ie only once per field if it's already there for nullability
            // but unclear if it's not that error path
            //
            if (!errorPaths.add(fieldPath)) {
                return;
            }
            this.errors.set(ImmutableKit.addToList(this.errors.get(), error));
        });
    }

    /**
     * This method will allow you to add errors into the running execution context, without a check
     * for per field unique-ness
     *
     * @param error the error to add
     */
    public void addError(GraphQLError error) {
        errorsLock.runLocked(() -> {
            // see https://github.com/graphql-java/graphql-java/issues/888 on how the spec is unclear
            // on how exactly multiple errors should be handled - ie only once per field or not outside the nullability
            // aspect.
            if (error.getPath() != null) {
                ResultPath path = ResultPath.fromList(error.getPath());
                this.errorPaths.add(path);
            }
            this.errors.set(ImmutableKit.addToList(this.errors.get(), error));
        });
    }

    /**
     * This method will allow you to add errors into the running execution context, without a check
     * for per field unique-ness
     *
     * @param errors the errors to add
     */
    public void addErrors(List<GraphQLError> errors) {
        if (errors.isEmpty()) {
            return;
        }
        // we are locked because we set two fields at once - but we only ever read one of them later
        // in getErrors so no need for synchronised there.
        errorsLock.runLocked(() -> {
            Set<ResultPath> newErrorPaths = new HashSet<>();
            for (GraphQLError error : errors) {
                // see https://github.com/graphql-java/graphql-java/issues/888 on how the spec is unclear
                // on how exactly multiple errors should be handled - ie only once per field or not outside the nullability
                // aspect.
                if (error.getPath() != null) {
                    ResultPath path = ResultPath.fromList(error.getPath());
                    newErrorPaths.add(path);
                }
            }
            this.errorPaths.addAll(newErrorPaths);
            this.errors.set(ImmutableKit.concatLists(this.errors.get(), errors));
        });
    }

    /**
     * @return the total list of errors for this execution context
     */
    public List<GraphQLError> getErrors() {
        return errors.get();
    }

    public ExecutionStrategy getQueryStrategy() {
        return queryStrategy;
    }

    public ExecutionStrategy getMutationStrategy() {
        return mutationStrategy;
    }

    public ExecutionStrategy getSubscriptionStrategy() {
        return subscriptionStrategy;
    }

    public IncrementalCallState getIncrementalCallState() {
        return incrementalCallState;
    }

    public ExecutionStrategy getStrategy(OperationDefinition.Operation operation) {
        if (operation == OperationDefinition.Operation.MUTATION) {
            return getMutationStrategy();
        } else if (operation == OperationDefinition.Operation.SUBSCRIPTION) {
            return getSubscriptionStrategy();
        } else {
            return getQueryStrategy();
        }
    }

    public Supplier<ExecutableNormalizedOperation> getNormalizedQueryTree() {
        return queryTree;
    }

    @Internal
    public void setDataLoaderDispatcherStrategy(DataLoaderDispatchStrategy dataLoaderDispatcherStrategy) {
        this.dataLoaderDispatcherStrategy = dataLoaderDispatcherStrategy;
    }

    @Internal
    public DataLoaderDispatchStrategy getDataLoaderDispatcherStrategy() {
        return dataLoaderDispatcherStrategy;
    }

    /**
     * This helps you transform the current ExecutionContext object into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new ExecutionContext object based on calling build on that builder
     */
    public ExecutionContext transform(Consumer<ExecutionContextBuilder> builderConsumer) {
        ExecutionContextBuilder builder = ExecutionContextBuilder.newExecutionContextBuilder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public ResultNodesInfo getResultNodesInfo() {
        return resultNodesInfo;
    }


    @Override
    public String toString() {
        return "ExecutionContext{" +
                " isRunning=" + isRunning() +
                " executionId=" + executionId +
                '}';
    }

    @Nullable
    EngineRunningObserver getEngineRunningObserver() {
        return engineRunningObserver;
    }

    @Internal
    public boolean isRunning() {
        return isRunning.get() > 0;
    }

    private void incrementRunning() {
        assertTrue(isRunning.get() >= 0);
        if (isRunning.incrementAndGet() == 1) {
            changeOfState(RUNNING);
        }
    }

    private void decrementRunning() {
        assertTrue(isRunning.get() > 0);
        if (isRunning.decrementAndGet() == 0) {
            changeOfState(NOT_RUNNING);
        }
    }

    @Internal
    public void incrementRunning(CompletableFuture<?> cf) {
        cf.whenComplete((result, throwable) -> {
            incrementRunning();
        });
    }

    @Internal
    public void decrementRunning(CompletableFuture<?> cf) {
        cf.whenComplete((result, throwable) -> {
            decrementRunning();
        });

    }

    /**
     * This method increments the engine state and then runs the {@link Supplier} to get a value
     * <p>
     * If the request has been cancelled then {@link AbortExecutionException} is thrown so
     * you better be prepared to handle that.  Do not do this inside {@link CompletableFuture} handling
     * say but rather use {@link ExecutionContext#engineHandle(Function, Function)}
     *
     * @param callable the code to run
     *
     * @return a value
     *
     * @throws AbortExecutionException is the operation has been cancelled
     */
    @Internal
    public <T> T engineCallOrCancel(Supplier<T> callable) throws AbortExecutionException {
        incrementRunning();
        throwIfCancelled();
        try {
            return callable.get();
        } finally {
            decrementRunning();
        }
    }

    /**
     * This will return a {@link BiFunction} that could be used in a {@link CompletableFuture#handle(BiFunction)}
     * that will run the good path if the value is ok or the bad path if there is a throwable.  If the request has been cancelled
     * then a {@link AbortExecutionException} will be created and the bad path will be run.
     *
     * @param goodPath the good path to run
     * @param badPath  the bad path to run
     * @param <T>      for two
     *
     * @return a {@link BiFunction}
     */
    @Internal
    public <T, U> BiFunction<? super T, Throwable, ? extends U> engineHandle(Function<? super T, ? extends U> goodPath,
                                                                             Function<Throwable, ? extends U> badPath) {
        return (value, throwable) -> {
            incrementRunning();
            throwable = checkIsCancelled(throwable);
            try {
                if (throwable == null) {
                    return goodPath.apply(value);
                } else {
                    return badPath.apply(throwable);
                }
            } finally {
                decrementRunning();
            }
        };
    }

    /**
     * This will return a {@link Function} that can be used say in a {@link CompletableFuture#exceptionally(Function)} situation
     * to turn an exception into a value.  The engine state will be incremented and decremented during around the callback
     *
     * @param fn  the function to make a value
     * @param <T> for two
     *
     * @return a function to turns exceptions into values
     */
    @Internal
    public <T> Function<Throwable, ? extends T> engineExceptionally(Function<Throwable, ? extends T> fn) {
        return (throwable) -> {
            incrementRunning();
            try {
                return fn.apply(throwable);
            } finally {
                decrementRunning();
            }
        };
    }

    /**
     * This method increments the engine state and then runs the {@link Runnable}
     *
     * If the request has been cancelled then {@link AbortExecutionException} is thrown so
     * you better be prepared to handle that.  Do do this inside {@link CompletableFuture} handling
     * say but rather use {@link ExecutionContext#engineRun(Consumer, Consumer)}
     *
     * @param runnable the code to run
     *
     * @throws AbortExecutionException is the operation has been cancelled
     */
    @Internal
    public void engineRunOrCancel(Runnable runnable) throws AbortExecutionException {
        incrementRunning();
        throwIfCancelled();
        try {
            runnable.run();
        } finally {
            decrementRunning();
            throwIfCancelled();
        }
    }

    /**
     * This will return a {@link BiConsumer} that could be used in a {@link CompletableFuture#whenComplete(BiConsumer)}
     * that will run the good path if the value is ok or the bad path if there is a throwable.  If the request has been cancelled
     * then a {@link AbortExecutionException} will be created and the bad path will be run.
     *
     * @param goodPath the good path to run
     * @param badPath  the bad path to run
     * @param <T>      for two
     *
     * @return a {@link BiConsumer}
     */
    @Internal
    public <T> BiConsumer<T, Throwable> engineRun(Consumer<T> goodPath,
                                                  Consumer<Throwable> badPath) {
        return (value, throwable) -> {
            incrementRunning();
            throwable = checkIsCancelled(throwable);
            try {
                if (throwable == null) {
                    goodPath.accept(value);
                } else {
                    badPath.accept(throwable);
                }
            } finally {
                decrementRunning();
            }
        };
    }

    private void throwIfCancelled() {
        AbortExecutionException abortExecutionException = checkIsCancelled();
        if (abortExecutionException != null) {
            throw abortExecutionException;
        }
    }

    private Throwable checkIsCancelled(Throwable currentThrowable) {
        // no need to check we are cancelled if we already have an exception in play
        // since it can lead to an exception being thrown when an exception has already been
        // thrown
        if (currentThrowable == null) {
            return checkIsCancelled();
        }
        return currentThrowable;
    }

    /**
     * This will abort the execution via {@link AbortExecutionException} if the {@link ExecutionInput} has been cancelled
     */
    private AbortExecutionException checkIsCancelled() {
        if (executionInput.isCancelled()) {
            changeOfState(CANCELLED);
            return new AbortExecutionException("Execution has been asked to be cancelled");
        }
        return null;
    }

    private void changeOfState(RunningState runningState) {
        if (engineRunningObserver != null) {
            engineRunningObserver.runningStateChanged(executionId, graphQLContext, runningState);
        }
    }
}
