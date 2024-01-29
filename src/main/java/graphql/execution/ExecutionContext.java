package graphql.execution;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.ExecutionInput;
import graphql.GraphQLContext;
import graphql.GraphQLError;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
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

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
    private final ValueUnboxer valueUnboxer;
    private final ExecutionInput executionInput;
    private final Supplier<ExecutableNormalizedOperation> queryTree;

    ExecutionContext(ExecutionContextBuilder builder) {
        this.graphQLSchema = builder.graphQLSchema;
        this.executionId = builder.executionId;
        this.instrumentationState = builder.instrumentationState;
        this.queryStrategy = builder.queryStrategy;
        this.mutationStrategy = builder.mutationStrategy;
        this.subscriptionStrategy = builder.subscriptionStrategy;
        this.fragmentsByName = builder.fragmentsByName;
        this.coercedVariables = builder.coercedVariables;
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
        queryTree = FpKit.interThreadMemoize(() -> ExecutableNormalizedOperationFactory.createExecutableNormalizedOperation(graphQLSchema, operationDefinition, fragmentsByName, coercedVariables));
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

    /**
     * @return map of coerced variables
     *
     * @deprecated use {@link #getCoercedVariables()} instead
     */
    @Deprecated(since = "2022-05-24")
    public Map<String, Object> getVariables() {
        return coercedVariables.toMap();
    }

    public CoercedVariables getCoercedVariables() {
        return coercedVariables;
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

    public ExecutionStrategy getQueryStrategy() { return queryStrategy; }

    public ExecutionStrategy getMutationStrategy() {
        return mutationStrategy;
    }

    public ExecutionStrategy getSubscriptionStrategy() {
        return subscriptionStrategy;
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
}
