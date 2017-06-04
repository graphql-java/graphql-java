package graphql.execution;


import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLException;
import graphql.Internal;
import graphql.MutationNotSupportedError;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationDataFetchParameters;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static graphql.execution.ExecutionStrategyParameters.newParameters;
import static graphql.execution.TypeInfo.newTypeInfo;
import static graphql.language.OperationDefinition.Operation.MUTATION;
import static graphql.language.OperationDefinition.Operation.QUERY;
import static graphql.language.OperationDefinition.Operation.SUBSCRIPTION;

@Internal
public class Execution {

    private final FieldCollector fieldCollector = new FieldCollector();
    private final ExecutionStrategy queryStrategy;
    private final ExecutionStrategy mutationStrategy;
    private final ExecutionStrategy subscriptionStrategy;
    private final Instrumentation instrumentation;

    public Execution(ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy, ExecutionStrategy subscriptionStrategy, Instrumentation instrumentation) {
        this.queryStrategy = queryStrategy != null ? queryStrategy : new SimpleExecutionStrategy();
        this.mutationStrategy = mutationStrategy != null ? mutationStrategy : new SimpleExecutionStrategy();
        this.subscriptionStrategy = subscriptionStrategy != null ? subscriptionStrategy : new SimpleExecutionStrategy();
        this.instrumentation = instrumentation;
    }

    public ExecutionResult execute(ExecutionId executionId, GraphQLSchema graphQLSchema, Object context, Object root, Document document, String operationName, Map<String, Object> args) {
        ExecutionContextBuilder executionContextBuilder = new ExecutionContextBuilder(new ValuesResolver(), instrumentation);
        ExecutionContext executionContext = executionContextBuilder
                .executionId(executionId)
                .build(graphQLSchema, queryStrategy, mutationStrategy, subscriptionStrategy, context, root, document, operationName, args);
        return executeOperation(executionContext, root, executionContext.getOperationDefinition());
    }

    private GraphQLObjectType getOperationRootType(GraphQLSchema graphQLSchema, OperationDefinition.Operation operation) {
        if (operation == MUTATION) {
            return graphQLSchema.getMutationType();

        } else if (operation == QUERY) {
            return graphQLSchema.getQueryType();

        } else if (operation == SUBSCRIPTION) {
            return graphQLSchema.getSubscriptionType();

        } else {
            throw new GraphQLException("Unhandled case.  An extra operation enum has been added without code support");
        }
    }

    private ExecutionResult executeOperation(
            ExecutionContext executionContext,
            Object root,
            OperationDefinition operationDefinition) {

        InstrumentationContext<ExecutionResult> dataFetchCtx = instrumentation.beginDataFetch(new InstrumentationDataFetchParameters(executionContext));

        OperationDefinition.Operation operation = operationDefinition.getOperation();
        GraphQLObjectType operationRootType = getOperationRootType(executionContext.getGraphQLSchema(), operation);

        //
        // do we have a mutation operation root type.  The spec says if we don't then mutations are not allowed to be executed
        //
        // for the record earlier code has asserted that we have a query type in the schema since the spec says this is
        // ALWAYS required
        if (operation == MUTATION && operationRootType == null) {
            return new ExecutionResultImpl(Collections.singletonList(new MutationNotSupportedError()));
        }

        FieldCollectorParameters collectorParameters = FieldCollectorParameters.newParameters(executionContext.getGraphQLSchema(), operationRootType)
                .fragments(executionContext.getFragmentsByName())
                .variables(executionContext.getVariables())
                .build();

        Map<String, List<Field>> fields = fieldCollector.collectFields(collectorParameters, operationDefinition.getSelectionSet());

        TypeInfo typeInfo = newTypeInfo().type(operationRootType).build();
        NonNullableFieldValidator nonNullableFieldValidator = new NonNullableFieldValidator(executionContext, typeInfo);

        ExecutionStrategyParameters parameters = newParameters()
                .typeInfo(typeInfo)
                .source(root)
                .fields(fields)
                .nonNullFieldValidator(nonNullableFieldValidator)
                .build();

        ExecutionResult result;
        try {
            if (operation == OperationDefinition.Operation.MUTATION) {
                result = mutationStrategy.execute(executionContext, parameters);
            } else if (operation == SUBSCRIPTION) {
                result = subscriptionStrategy.execute(executionContext, parameters);
            } else {
                result = queryStrategy.execute(executionContext, parameters);
            }
        } catch (NonNullableFieldWasNullException e) {
            // this means it was non null types all the way from an offending non null type
            // up to the root object type and there was a a null value some where.
            //
            // The spec says we should return null for the data in this case
            //
            // http://facebook.github.io/graphql/#sec-Errors-and-Non-Nullability
            //
            result = new ExecutionResultImpl(null, executionContext.getErrors());
        }

        dataFetchCtx.onEnd(result);
        return result;
    }
}
