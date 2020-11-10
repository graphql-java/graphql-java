package graphql.execution.instrumentation.fieldvalidation;

import com.google.common.collect.ImmutableList;
import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.analysis.QueryTraverser;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorStub;
import graphql.execution.ExecutionContext;
import graphql.execution.ResultPath;
import graphql.language.Field;
import graphql.language.SourceLocation;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Internal
class FieldValidationSupport {

    static List<GraphQLError> validateFieldsAndArguments(FieldValidation fieldValidation, ExecutionContext executionContext) {

        Map<ResultPath, List<FieldAndArguments>> fieldArgumentsMap = new LinkedHashMap<>();

        QueryTraverser queryTraverser = QueryTraverser.newQueryTraverser()
                .schema(executionContext.getGraphQLSchema())
                .document(executionContext.getDocument())
                .operationName(executionContext.getOperationDefinition().getName())
                .variables(executionContext.getVariables())
                .build();

        queryTraverser.visitPreOrder(new QueryVisitorStub() {
            @Override
            public void visitField(QueryVisitorFieldEnvironment env) {
                Field field = env.getField();
                if (field.getArguments() != null && !field.getArguments().isEmpty()) {
                    //
                    // only fields that have arguments make any sense to placed in play
                    // since only they have variable input
                    FieldAndArguments fieldArguments = new FieldAndArgumentsImpl(env);
                    ResultPath path = fieldArguments.getPath();
                    List<FieldAndArguments> list = fieldArgumentsMap.getOrDefault(path, new ArrayList<>());
                    list.add(fieldArguments);
                    fieldArgumentsMap.put(path, list);
                }
            }
        });

        FieldValidationEnvironment environment = new FieldValidationEnvironmentImpl(executionContext, fieldArgumentsMap);
        //
        // this will allow a consumer to plugin their own validation of fields and arguments
        return fieldValidation.validateFields(environment);
    }

    private static class FieldAndArgumentsImpl implements FieldAndArguments {
        private final QueryVisitorFieldEnvironment traversalEnv;
        private final FieldAndArguments parentArgs;
        private final ResultPath path;

        FieldAndArgumentsImpl(QueryVisitorFieldEnvironment traversalEnv) {
            this.traversalEnv = traversalEnv;
            this.parentArgs = mkParentArgs(traversalEnv);
            this.path = mkPath(traversalEnv);
        }

        private FieldAndArguments mkParentArgs(QueryVisitorFieldEnvironment traversalEnv) {
            return traversalEnv.getParentEnvironment() != null ? new FieldAndArgumentsImpl(traversalEnv.getParentEnvironment()) : null;
        }

        private ResultPath mkPath(QueryVisitorFieldEnvironment traversalEnv) {
            QueryVisitorFieldEnvironment parentEnvironment = traversalEnv.getParentEnvironment();
            if (parentEnvironment == null) {
                return ResultPath.rootPath().segment(traversalEnv.getField().getName());
            } else {
                Deque<QueryVisitorFieldEnvironment> stack = new ArrayDeque<>();
                stack.push(traversalEnv);
                while (parentEnvironment != null) {
                    stack.push(parentEnvironment);
                    parentEnvironment = parentEnvironment.getParentEnvironment();
                }
                ResultPath path = ResultPath.rootPath();
                while (!stack.isEmpty()) {
                    QueryVisitorFieldEnvironment environment = stack.pop();
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
            return traversalEnv.getFieldsContainer();
        }

        @Override
        public ResultPath getPath() {
            return path;
        }

        @Override
        public Map<String, Object> getArgumentValuesByName() {
            return traversalEnv.getArguments();
        }

        @Override
        @SuppressWarnings("TypeParameterUnusedInFormals")
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
        private final Map<ResultPath, List<FieldAndArguments>> fieldArgumentsMap;
        private final ImmutableList<FieldAndArguments> fieldArguments;

        FieldValidationEnvironmentImpl(ExecutionContext executionContext, Map<ResultPath, List<FieldAndArguments>> fieldArgumentsMap) {
            this.executionContext = executionContext;
            this.fieldArgumentsMap = fieldArgumentsMap;
            this.fieldArguments = fieldArgumentsMap.values().stream().flatMap(List::stream).collect(ImmutableList.toImmutableList());
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
        public Map<ResultPath, List<FieldAndArguments>> getFieldsByPath() {
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

        FieldAndArgError(String message, Field field, ResultPath path) {
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
