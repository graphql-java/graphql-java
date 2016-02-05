package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.language.Field;
import graphql.schema.GraphQLObjectType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SimpleExecutionStrategy extends ExecutionStrategy {
    @Override
    public ExecutionResult execute(ExecutionContext executionContext, GraphQLObjectType parentType, Object source, Map<String, List<Field>> fields) {
        Map<String, Object> results = new LinkedHashMap<String, Object>();
        for (String fieldName : fields.keySet()) {
            List<Field> fieldList = fields.get(fieldName);
            ExecutionResult resolvedResult = resolveField(executionContext, parentType, source, fieldList);

            results.put(fieldName, resolvedResult != null ? resolvedResult.getData() : null);
        }
        return new ExecutionResultImpl(results, executionContext.getErrors());
    }
}
