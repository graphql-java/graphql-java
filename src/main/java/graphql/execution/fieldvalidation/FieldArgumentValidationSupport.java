package graphql.execution.fieldvalidation;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.analysis.QueryTraversal;
import graphql.analysis.QueryVisitorEnvironment;
import graphql.execution.ExecutionPath;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.SourceLocation;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchema;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

@Internal
public class FieldArgumentValidationSupport {

    public static List<GraphQLError> validateFieldsAndArguments(FieldAndArgumentsValidator fieldAndArgumentsValidator, GraphQLSchema schema, Document document, OperationDefinition operationDefinition, Map<String, Object> coercedVariables) {

        Map<ExecutionPath, FieldAndArguments> fieldArgumentsMap = new HashMap<>();

        QueryTraversal queryTraversal = new QueryTraversal(schema, document, operationDefinition.getName(), coercedVariables);
        queryTraversal.visitPreOrder(traversalEnv -> {
            Field field = traversalEnv.getField();
            if (field.getArguments() != null && !field.getArguments().isEmpty()) {
                //
                // only fields that have arguments make any sense to placed in play
                // since only they have variable input
                FieldAndArguments fieldArguments = new FieldAndArgumentsImpl(traversalEnv);
                fieldArgumentsMap.put(fieldArguments.getPath(), fieldArguments);
            }
        });

        FieldAndArgumentsValidationEnvironment environment = new FieldAndArgumentsValidationEnvironmentImpl(schema, operationDefinition, fieldArgumentsMap);
        //
        // this will allow a consumer to plugin their own validation of fields and arguments
        return fieldAndArgumentsValidator.validateFieldArguments(environment);
    }

    private static class FieldAndArgumentsImpl implements FieldAndArguments {
        private final QueryVisitorEnvironment traversalEnv;
        private final FieldAndArguments parentArgs;
        private final ExecutionPath path;

        FieldAndArgumentsImpl(QueryVisitorEnvironment traversalEnv) {
            this.traversalEnv = traversalEnv;
            this.parentArgs = mkParentArgs(traversalEnv);
            this.path = mkPath(traversalEnv);
        }

        private FieldAndArguments mkParentArgs(QueryVisitorEnvironment traversalEnv) {
            return traversalEnv.getParentEnvironment() != null ? new FieldAndArgumentsImpl(traversalEnv.getParentEnvironment()) : null;
        }

        private ExecutionPath mkPath(QueryVisitorEnvironment traversalEnv) {
            QueryVisitorEnvironment parentEnvironment = traversalEnv.getParentEnvironment();
            if (parentEnvironment == null) {
                return ExecutionPath.rootPath().segment(traversalEnv.getField().getName());
            } else {
                Stack<QueryVisitorEnvironment> stack = new Stack<>();
                stack.push(traversalEnv);
                while (parentEnvironment != null) {
                    stack.push(parentEnvironment);
                    parentEnvironment = parentEnvironment.getParentEnvironment();
                }
                ExecutionPath path = ExecutionPath.rootPath();
                while (!stack.isEmpty()) {
                    QueryVisitorEnvironment environment = stack.pop();
                    path = path.segment(environment.getField().getName());
                }
                return path;
            }
        }

        @Override
        public Field getField() {
            return traversalEnv.getField();
        }

        @Override
        public GraphQLFieldDefinition getFieldDefinition() {
            return traversalEnv.getFieldDefinition();
        }

        @Override
        public GraphQLCompositeType getParentType() {
            return traversalEnv.getParentType();
        }

        @Override
        public ExecutionPath getPath() {
            return path;
        }

        @Override
        public Map<String, Object> getFieldArgumentValues() {
            return traversalEnv.getArguments();
        }

        @Override
        public FieldAndArguments getParentFieldAndArguments() {
            return parentArgs;
        }
    }

    private static class FieldAndArgumentsValidationEnvironmentImpl implements FieldAndArgumentsValidationEnvironment {
        private final GraphQLSchema schema;
        private final OperationDefinition operationDefinition;
        private final Map<ExecutionPath, FieldAndArguments> fieldArgumentsMap;

        FieldAndArgumentsValidationEnvironmentImpl(GraphQLSchema schema, OperationDefinition operationDefinition, Map<ExecutionPath, FieldAndArguments> fieldArgumentsMap) {
            this.schema = schema;
            this.operationDefinition = operationDefinition;
            this.fieldArgumentsMap = fieldArgumentsMap;
        }

        @Override
        public GraphQLSchema getSchema() {
            return schema;
        }

        @Override
        public OperationDefinition getOperation() {
            return operationDefinition;
        }

        @Override
        public Map<ExecutionPath, FieldAndArguments> getFieldArguments() {
            return fieldArgumentsMap;
        }

        @Override
        public GraphQLError mkError(String msg, Field field, ExecutionPath path) {
            return new FieldAndArgError(msg, field, path);
        }

        private static class FieldAndArgError implements GraphQLError {
            private final String message;
            private final List<SourceLocation> locations;
            private final List<Object> path;

            public FieldAndArgError(String message, Field field, ExecutionPath path) {
                this.message = message;
                this.locations = field == null ? null : Collections.singletonList(field.getSourceLocation());
                this.path = path == null ? null : path.toList();
            }

            @Override
            public String getMessage() {
                return message;
            }

            @Override
            public ErrorType getErrorType() {
                return ErrorType.ValidationError;
            }

            @Override
            public List<SourceLocation> getLocations() {
                return locations;
            }

            @Override
            public List<Object> getPath() {
                return path;
            }
        }
    }
}
