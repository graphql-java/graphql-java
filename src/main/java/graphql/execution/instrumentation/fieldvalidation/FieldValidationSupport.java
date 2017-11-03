package graphql.execution.instrumentation.fieldvalidation;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.analysis.QueryTraversal;
import graphql.analysis.QueryVisitorEnvironment;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionPath;
import graphql.language.Field;
import graphql.language.SourceLocation;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Collectors;

import static graphql.schema.visibility.GraphqlFieldVisibilityEnvironment.newEnvironment;

@Internal
class FieldValidationSupport {

    static List<GraphQLError> validateFieldsAndArguments(FieldValidation fieldValidation, ExecutionContext executionContext) {

        Map<ExecutionPath, List<FieldAndArguments>> fieldArgumentsMap = new LinkedHashMap<>();

        QueryTraversal queryTraversal = new QueryTraversal(
                executionContext.getGraphQLSchema(),
                executionContext.getDocument(),
                executionContext.getOperationDefinition().getName(),
                executionContext.getVariables()
        );
        queryTraversal.visitPreOrder(traversalEnv -> {
            Field field = traversalEnv.getField();
            if (field.getArguments() != null && !field.getArguments().isEmpty()) {
                //
                // only fields that have arguments make any sense to placed in play
                // since only they have variable input
                FieldAndArguments fieldArguments = new FieldAndArgumentsImpl(traversalEnv);
                ExecutionPath path = fieldArguments.getPath();
                List<FieldAndArguments> list = fieldArgumentsMap.getOrDefault(path, new ArrayList<>());
                list.add(fieldArguments);
                fieldArgumentsMap.put(path, list);
            }
        }, newEnvironment().setContext(executionContext.getContext()).build());

        FieldValidationEnvironment environment = new FieldValidationEnvironmentImpl(executionContext, fieldArgumentsMap);
        //
        // this will allow a consumer to plugin their own validation of fields and arguments
        return fieldValidation.validateFields(environment);
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
        public Map<String, Object> getArgumentValuesByName() {
            return traversalEnv.getArguments();
        }

        @Override
        public <T> T getArgumentValue(String argumentName) {
            //noinspection unchecked
            return (T) traversalEnv.getArguments().get(argumentName);
        }

        @Override
        public FieldAndArguments getParentFieldAndArguments() {
            return parentArgs;
        }
    }

    private static class FieldValidationEnvironmentImpl implements FieldValidationEnvironment {
        private final ExecutionContext executionContext;
        private final Map<ExecutionPath, List<FieldAndArguments>> fieldArgumentsMap;
        private final List<FieldAndArguments> fieldArguments;

        FieldValidationEnvironmentImpl(ExecutionContext executionContext, Map<ExecutionPath, List<FieldAndArguments>> fieldArgumentsMap) {
            this.executionContext = executionContext;
            this.fieldArgumentsMap = fieldArgumentsMap;
            this.fieldArguments = fieldArgumentsMap.values().stream().flatMap(List::stream).collect(Collectors.toList());
        }


        @Override
        public ExecutionContext getExecutionContext() {
            return executionContext;
        }

        @Override
        public List<FieldAndArguments> getFields() {
            return fieldArguments;
        }

        @Override
        public Map<ExecutionPath, List<FieldAndArguments>> getFieldsByPath() {
            return fieldArgumentsMap;
        }

        @Override
        public GraphQLError mkError(String msg) {
            return new FieldAndArgError(msg, null, null);
        }

        @Override
        public GraphQLError mkError(String msg, FieldAndArguments fieldAndArguments) {
            return new FieldAndArgError(msg, fieldAndArguments.getField(), fieldAndArguments.getPath());
        }
    }

    private static class FieldAndArgError implements GraphQLError {
        private final String message;
        private final List<SourceLocation> locations;
        private final List<Object> path;

        FieldAndArgError(String message, Field field, ExecutionPath path) {
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
