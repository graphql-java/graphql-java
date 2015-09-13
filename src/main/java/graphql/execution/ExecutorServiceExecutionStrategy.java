package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLException;
import graphql.language.Field;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class ExecutorServiceExecutionStrategy extends ExecutionStrategy {

    ExecutorService executorService;

    public ExecutorServiceExecutionStrategy(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public ExecutionResult execute(final ExecutionContext executionContext, final GraphQLObjectType parentType, final Object source, final Map<String, List<Field>> fields) {
        if (executorService == null) return new SimpleExecutionStrategy().execute(executionContext, parentType, source, fields);

        Map<String, Future<ExecutionResult>> futures = new LinkedHashMap<>();
        for (String fieldName : fields.keySet()) {
            final List<Field> fieldList = fields.get(fieldName);
            Callable<ExecutionResult> resolveField = new Callable<ExecutionResult>() {
                @Override
                public ExecutionResult call() throws Exception {
                    return resolveField(executionContext, parentType, source, fieldList);

                }
            };
            futures.put(fieldName, executorService.submit(resolveField));
        }
        try {
            Map<String, Object> results = new LinkedHashMap<>();
            for (String fieldName : futures.keySet()) {
                ExecutionResult executionResult = futures.get(fieldName).get();

                results.put(fieldName, executionResult != null ? executionResult.getData() : null);
            }
            return new ExecutionResultImpl(results, executionContext.getErrors());
        } catch (InterruptedException | ExecutionException e) {
            throw new GraphQLException(e);
        }
    }
}
