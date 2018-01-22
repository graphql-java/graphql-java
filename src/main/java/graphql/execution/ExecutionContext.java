package graphql.execution;


import graphql.GraphQLError;
import graphql.PublicApi;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@PublicApi
public class ExecutionContext {

    private final GraphQLSchema graphQLSchema;
    private final ExecutionId executionId;
    private final InstrumentationState instrumentationState;
    private final ExecutionStrategy queryStrategy;
    private final ExecutionStrategy mutationStrategy;
    private final ExecutionStrategy subscriptionStrategy;
    private final Map<String, FragmentDefinition> fragmentsByName;
    private final OperationDefinition operationDefinition;
    private final Document document;
    private final Map<String, Object> variables;
    private final Object root;
    private final Object context;
    private final Instrumentation instrumentation;
    private final Map<String, GraphQLError> perFieldErrors = new LinkedHashMap<>();
    private final List<GraphQLError> otherErrors = new ArrayList<>();

    public ExecutionContext(Instrumentation instrumentation, ExecutionId executionId, GraphQLSchema graphQLSchema, InstrumentationState instrumentationState, ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy, ExecutionStrategy subscriptionStrategy, Map<String, FragmentDefinition> fragmentsByName, Document document, OperationDefinition operationDefinition, Map<String, Object> variables, Object context, Object root) {
        this(instrumentation, executionId, graphQLSchema, instrumentationState, queryStrategy, mutationStrategy, subscriptionStrategy, fragmentsByName, document, operationDefinition, variables, context, root, Collections.emptyMap(), Collections.emptyList());
    }

    ExecutionContext(Instrumentation instrumentation, ExecutionId executionId, GraphQLSchema graphQLSchema, InstrumentationState instrumentationState, ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy, ExecutionStrategy subscriptionStrategy, Map<String, FragmentDefinition> fragmentsByName, Document document, OperationDefinition operationDefinition, Map<String, Object> variables, Object context, Object root, Map<String, GraphQLError> startingFieldErrors, List<GraphQLError> startingErrors) {
        this.graphQLSchema = graphQLSchema;
        this.executionId = executionId;
        this.instrumentationState = instrumentationState;
        this.queryStrategy = queryStrategy;
        this.mutationStrategy = mutationStrategy;
        this.subscriptionStrategy = subscriptionStrategy;
        this.fragmentsByName = fragmentsByName;
        this.document = document;
        this.operationDefinition = operationDefinition;
        this.variables = variables;
        this.context = context;
        this.root = root;
        this.instrumentation = instrumentation;
        this.perFieldErrors.putAll(startingFieldErrors);
        this.otherErrors.addAll(startingErrors);
    }


    public ExecutionId getExecutionId() {
        return executionId;
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

    public Map<String, Object> getVariables() {
        return variables;
    }

    public Object getContext() {
        return context;
    }

    @SuppressWarnings("unchecked")
    public <T> T getRoot() {
        return (T) root;
    }

    public FragmentDefinition getFragment(String name) {
        return fragmentsByName.get(name);
    }

    /**
     * This method will only put one error per field path.
     *
     * @param error     the error to add
     * @param fieldPath the field path to put it under
     */
    public void addError(GraphQLError error, ExecutionPath fieldPath) {
        // see http://facebook.github.io/graphql/#sec-Errors-and-Non-Nullability about how per
        // field errors should be handled - ie only once per field
        synchronized (this) {
            String key = fieldPath.toString();
            if (!perFieldErrors.containsKey(key)) {
                this.perFieldErrors.put(key, error);
            }
        }
    }

    /**
     * This method will allow you to add errors into the running execution context, without a check
     * for per field unique-ness
     *
     * @param error the error to add
     */
    public void addError(GraphQLError error) {
        // see https://github.com/graphql-java/graphql-java/issues/888 on how the spec is unclear
        // on how multiple errors should be handled - ie only once per field or not outside nullability
        synchronized (this) {
            this.otherErrors.add(error);
        }
    }

    // access for builder only
    Map<String, GraphQLError> getPerFieldErrorMap() {
        synchronized (this) {
            return perFieldErrors;
        }
    }

    // access for builder only
    List<GraphQLError> getOtherErrors() {
        synchronized (this) {
            return otherErrors;
        }
    }


    /**
     * @return the total list of errors for this execution context
     */
    public List<GraphQLError> getErrors() {
        synchronized (this) {
            ArrayList<GraphQLError> errList = new ArrayList<>();
            errList.addAll(perFieldErrors.values());
            errList.addAll(otherErrors);
            return errList;
        }
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
