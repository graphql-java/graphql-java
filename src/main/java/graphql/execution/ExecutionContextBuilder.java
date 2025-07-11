package graphql.execution;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.EngineRunningState;
import graphql.ExecutionInput;
import graphql.ExperimentalApi;
import graphql.GraphQLContext;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.collect.ImmutableKit;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.normalized.nf.NormalizedDocumentFactory;
import graphql.normalized.nf.provider.NormalizedDocumentProvider;
import graphql.schema.GraphQLSchema;
import org.dataloader.DataLoaderRegistry;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static graphql.Assert.assertNotNull;
import static graphql.collect.ImmutableKit.emptyList;

@Internal
public class ExecutionContextBuilder {

    Instrumentation instrumentation;
    ExecutionId executionId;
    InstrumentationState instrumentationState;
    GraphQLSchema graphQLSchema;
    ExecutionStrategy queryStrategy;
    ExecutionStrategy mutationStrategy;
    ExecutionStrategy subscriptionStrategy;
    Object context;
    GraphQLContext graphQLContext;
    Object root;
    Document document;
    OperationDefinition operationDefinition;
    CoercedVariables coercedVariables = CoercedVariables.emptyVariables();
    Supplier<NormalizedVariables> normalizedVariables = NormalizedVariables::emptyVariables;
    ImmutableMap<String, FragmentDefinition> fragmentsByName = ImmutableKit.emptyMap();
    DataLoaderRegistry dataLoaderRegistry;
    Locale locale;
    ImmutableList<GraphQLError> errors = emptyList();
    ValueUnboxer valueUnboxer;
    Object localContext;
    ExecutionInput executionInput;
    DataLoaderDispatchStrategy dataLoaderDispatcherStrategy = DataLoaderDispatchStrategy.NO_OP;
    boolean propagateErrorsOnNonNullContractFailure = true;
    EngineRunningState engineRunningState;
    ResponseMapFactory responseMapFactory = ResponseMapFactory.DEFAULT;
    NormalizedDocumentProvider normalizedDocumentProvider;

    /**
     * @return a new builder of {@link graphql.execution.ExecutionContext}s
     */
    public static ExecutionContextBuilder newExecutionContextBuilder() {
        return new ExecutionContextBuilder();
    }

    /**
     * Creates a new builder based on a previous execution context
     *
     * @param other the previous execution to clone
     *
     * @return a new builder of {@link graphql.execution.ExecutionContext}s
     */
    public static ExecutionContextBuilder newExecutionContextBuilder(ExecutionContext other) {
        return new ExecutionContextBuilder(other);
    }

    @Internal
    public ExecutionContextBuilder() {
    }

    @Internal
    ExecutionContextBuilder(ExecutionContext other) {
        instrumentation = other.getInstrumentation();
        executionId = other.getExecutionId();
        instrumentationState = other.getInstrumentationState();
        graphQLSchema = other.getGraphQLSchema();
        queryStrategy = other.getQueryStrategy();
        mutationStrategy = other.getMutationStrategy();
        subscriptionStrategy = other.getSubscriptionStrategy();
        context = other.getContext();
        graphQLContext = other.getGraphQLContext();
        localContext = other.getLocalContext();
        root = other.getRoot();
        document = other.getDocument();
        operationDefinition = other.getOperationDefinition();
        coercedVariables = other.getCoercedVariables();
        fragmentsByName = ImmutableMap.copyOf(other.getFragmentsByName());
        dataLoaderRegistry = other.getDataLoaderRegistry();
        locale = other.getLocale();
        errors = ImmutableList.copyOf(other.getErrors());
        valueUnboxer = other.getValueUnboxer();
        executionInput = other.getExecutionInput();
        dataLoaderDispatcherStrategy = other.getDataLoaderDispatcherStrategy();
        propagateErrorsOnNonNullContractFailure = other.propagateErrorsOnNonNullContractFailure();
        engineRunningState = other.getEngineRunningState();
        responseMapFactory = other.getResponseMapFactory();
        normalizedDocumentProvider = other.getNormalizedDocumentProvider();
    }

    public ExecutionContextBuilder instrumentation(Instrumentation instrumentation) {
        this.instrumentation = instrumentation;
        return this;
    }

    public ExecutionContextBuilder instrumentationState(InstrumentationState instrumentationState) {
        this.instrumentationState = instrumentationState;
        return this;
    }

    public ExecutionContextBuilder executionId(ExecutionId executionId) {
        this.executionId = executionId;
        return this;
    }

    public ExecutionContextBuilder graphQLSchema(GraphQLSchema graphQLSchema) {
        this.graphQLSchema = graphQLSchema;
        return this;
    }

    public ExecutionContextBuilder queryStrategy(ExecutionStrategy queryStrategy) {
        this.queryStrategy = queryStrategy;
        return this;
    }

    public ExecutionContextBuilder mutationStrategy(ExecutionStrategy mutationStrategy) {
        this.mutationStrategy = mutationStrategy;
        return this;
    }

    public ExecutionContextBuilder subscriptionStrategy(ExecutionStrategy subscriptionStrategy) {
        this.subscriptionStrategy = subscriptionStrategy;
        return this;
    }

    /*
     * @deprecated use {@link #graphQLContext(GraphQLContext)} instead
     */
    @Deprecated(since = "2021-07-05")
    public ExecutionContextBuilder context(@Nullable Object context) {
        this.context = context;
        return this;
    }

    public ExecutionContextBuilder graphQLContext(GraphQLContext context) {
        this.graphQLContext = context;
        return this;
    }

    public ExecutionContextBuilder localContext(Object localContext) {
        this.localContext = localContext;
        return this;
    }

    public ExecutionContextBuilder root(Object root) {
        this.root = root;
        return this;
    }

    /**
     * @param variables map of already coerced variables
     * @return this builder
     *
     * @deprecated use {@link #coercedVariables(CoercedVariables)} instead
     */
    @Deprecated(since = "2022-05-24")
    public ExecutionContextBuilder variables(Map<String, Object> variables) {
        this.coercedVariables = CoercedVariables.of(variables);
        return this;
    }

    public ExecutionContextBuilder coercedVariables(CoercedVariables coercedVariables) {
        this.coercedVariables = coercedVariables;
        return this;
    }

    public ExecutionContextBuilder normalizedVariableValues(Supplier<NormalizedVariables> normalizedVariables) {
        this.normalizedVariables = normalizedVariables;
        return this;
    }

    public ExecutionContextBuilder fragmentsByName(Map<String, FragmentDefinition> fragmentsByName) {
        this.fragmentsByName = ImmutableMap.copyOf(fragmentsByName);
        return this;
    }

    public ExecutionContextBuilder document(Document document) {
        this.document = document;
        return this;
    }

    public ExecutionContextBuilder operationDefinition(OperationDefinition operationDefinition) {
        this.operationDefinition = operationDefinition;
        return this;
    }

    public ExecutionContextBuilder dataLoaderRegistry(DataLoaderRegistry dataLoaderRegistry) {
        this.dataLoaderRegistry = assertNotNull(dataLoaderRegistry);
        return this;
    }

    public ExecutionContextBuilder locale(Locale locale) {
        this.locale = locale;
        return this;
    }

    public ExecutionContextBuilder valueUnboxer(ValueUnboxer valueUnboxer) {
        this.valueUnboxer = valueUnboxer;
        return this;
    }

    public ExecutionContextBuilder executionInput(ExecutionInput executionInput) {
        this.executionInput = executionInput;
        return this;
    }

    @Internal
    public ExecutionContextBuilder dataLoaderDispatcherStrategy(DataLoaderDispatchStrategy dataLoaderDispatcherStrategy) {
        this.dataLoaderDispatcherStrategy = dataLoaderDispatcherStrategy;
        return this;
    }

    @Internal
    public ExecutionContextBuilder responseMapFactory(ResponseMapFactory responseMapFactory) {
        this.responseMapFactory = responseMapFactory;
        return this;
    }

    @Internal
    public ExecutionContextBuilder normalizedDocumentProvider(NormalizedDocumentProvider normalizedDocumentProvider) {
        this.normalizedDocumentProvider = normalizedDocumentProvider;
        return this;
    }

    public ExecutionContextBuilder resetErrors() {
        this.errors = emptyList();
        return this;
    }

    @ExperimentalApi
    public ExecutionContextBuilder propagapropagateErrorsOnNonNullContractFailureeErrors(boolean propagateErrorsOnNonNullContractFailure) {
        this.propagateErrorsOnNonNullContractFailure = propagateErrorsOnNonNullContractFailure;
        return this;
    }


    public ExecutionContext build() {
        // preconditions
        assertNotNull(executionId, () -> "You must provide a query identifier");
        return new ExecutionContext(this);
    }

    public ExecutionContextBuilder engineRunningState(EngineRunningState engineRunningState) {
        this.engineRunningState = engineRunningState;
        return this;
    }
}
