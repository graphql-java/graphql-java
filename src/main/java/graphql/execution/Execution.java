package graphql.execution;


import graphql.ExecutionResult;
import graphql.GraphQLException;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;

public class Execution {

    private final FieldCollector fieldCollector;
    private final ExecutionStrategy queryStrategy;
    private final ExecutionStrategy mutationStrategy;
    private final ExecutionConstraints executionConstraints;

    private Execution(ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy, FieldCollector fieldCollector, ExecutionConstraints executionConstraints) {
        this.fieldCollector = fieldCollector;
        this.queryStrategy = queryStrategy;
        this.mutationStrategy = mutationStrategy;
        this.executionConstraints = executionConstraints;
    }

    public static Builder newExecution() {
        return new Builder();
    }

    public static class Builder {
        private final FieldCollector fieldCollector = new FieldCollector();
        private ExecutionConstraints executionConstraints = ExecutionConstraints.newConstraints().build();
        private ExecutionStrategy queryStrategy;
        private ExecutionStrategy mutationStrategy;

        public Builder queryStrategy(ExecutionStrategy queryStrategy) {
            this.queryStrategy = assertNotNull(queryStrategy);
            return this;
        }

        public Builder mutationStrategy(ExecutionStrategy mutationStrategy) {
            this.mutationStrategy = assertNotNull(mutationStrategy);
            return this;
        }

        public Builder executionConstraints(ExecutionConstraints executionConstraints) {
            this.executionConstraints = executionConstraints;
            return this;
        }

        public Execution build() {
            return new Execution(queryStrategy, mutationStrategy, fieldCollector, executionConstraints);
        }
    }


    public ExecutionResult execute(ExecutionId executionId, GraphQLSchema graphQLSchema, Object root, Document document, String operationName, Map<String, Object> args) {
        ExecutionContextBuilder executionContextBuilder = new ExecutionContextBuilder();
        ExecutionContext executionContext = executionContextBuilder
                .executionId(executionId)
                .executionConstraints(executionConstraints)
                .build(graphQLSchema, queryStrategy, mutationStrategy, root, document, operationName, args);
        return executeOperation(executionContext, root, executionContext.getOperationDefinition());
    }

    private GraphQLObjectType getOperationRootType(GraphQLSchema graphQLSchema, OperationDefinition operationDefinition) {
        if (operationDefinition.getOperation() == OperationDefinition.Operation.MUTATION) {
            return graphQLSchema.getMutationType();

        } else if (operationDefinition.getOperation() == OperationDefinition.Operation.QUERY) {
            return graphQLSchema.getQueryType();

        } else {
            throw new GraphQLException();
        }
    }

    private ExecutionResult executeOperation(
            ExecutionContext executionContext,
            Object root,
            OperationDefinition operationDefinition) {
        GraphQLObjectType operationRootType = getOperationRootType(executionContext.getGraphQLSchema(), operationDefinition);

        Map<String, List<Field>> fields = new LinkedHashMap<String, List<Field>>();
        fieldCollector.collectFields(executionContext, operationRootType, operationDefinition.getSelectionSet(), new ArrayList<String>(), fields);

        if (operationDefinition.getOperation() == OperationDefinition.Operation.MUTATION) {
            return mutationStrategy.execute(executionContext, operationRootType, root, fields);
        } else {
            return queryStrategy.execute(executionContext, operationRootType, root, fields);
        }
    }
}
