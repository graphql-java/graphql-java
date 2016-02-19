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

/**
 * <p>Execution class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class Execution {

    private FieldCollector fieldCollector = new FieldCollector();
    private ExecutionStrategy strategy;

    /**
     * <p>Constructor for Execution.</p>
     *
     * @param strategy a {@link graphql.execution.ExecutionStrategy} object.
     */
    public Execution(ExecutionStrategy strategy) {
        this.strategy = strategy;

        if (this.strategy == null) {
            this.strategy = new SimpleExecutionStrategy();
        }
    }

    /**
     * <p>execute.</p>
     *
     * @param graphQLSchema a {@link graphql.schema.GraphQLSchema} object.
     * @param root a {@link java.lang.Object} object.
     * @param document a {@link graphql.language.Document} object.
     * @param operationName a {@link java.lang.String} object.
     * @param args a {@link java.util.Map} object.
     * @return a {@link graphql.ExecutionResult} object.
     */
    public ExecutionResult execute(GraphQLSchema graphQLSchema, Object root, Document document, String operationName, Map<String, Object> args) {
        ExecutionContextBuilder executionContextBuilder = new ExecutionContextBuilder(new ValuesResolver());
        ExecutionContext executionContext = executionContextBuilder.build(graphQLSchema, strategy, root, document, operationName, args);
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
        GraphQLObjectType operationRootType = getOperationRootType(executionContext.getGraphQLSchema(), executionContext.getOperationDefinition());

        Map<String, List<Field>> fields = new LinkedHashMap<>();
        fieldCollector.collectFields(executionContext, operationRootType, operationDefinition.getSelectionSet(), new ArrayList<String>(), fields);

        if (operationDefinition.getOperation() == OperationDefinition.Operation.MUTATION) {
            return new SimpleExecutionStrategy().execute(executionContext, operationRootType, root, fields);
        } else {
            return strategy.execute(executionContext, operationRootType, root, fields);
        }
    }
}
