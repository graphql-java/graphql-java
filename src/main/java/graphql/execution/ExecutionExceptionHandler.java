package graphql.execution;

import graphql.schema.GraphQLFieldDefinition;

import java.util.Map;

@FunctionalInterface
public interface ExecutionExceptionHandler {

    void handleDataFetchingException(
            ExecutionContext executionContext,
            GraphQLFieldDefinition fieldDef,
            Map<String, Object> argumentValues,
            ExecutionPath path,
            Exception e
    );
}
