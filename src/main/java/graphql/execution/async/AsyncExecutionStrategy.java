package graphql.execution.async;

import graphql.ExceptionWhileDataFetching;
import graphql.ExecutionResult;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategy;
import graphql.language.Field;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toMap;


public final class AsyncExecutionStrategy extends ExecutionStrategy {

    public static AsyncExecutionStrategy serial() {
        return new AsyncExecutionStrategy(true);
    }

    public static AsyncExecutionStrategy parallel() {
        return new AsyncExecutionStrategy(false);
    }

    private static final Logger log = LoggerFactory.getLogger(AsyncExecutionStrategy.class);

    private final boolean serial;

    private AsyncExecutionStrategy(boolean serial) {
        this.serial = serial;
    }

    @Override
    public ExecutionResult execute(ExecutionContext executionContext, GraphQLObjectType parentType, Object source, Map<String, List<Field>> fields) {
        Map<String, Supplier<CompletionStage<ExecutionResult>>> resolvers = fields.entrySet().stream()
          .collect(toMap(Map.Entry::getKey, entry -> () -> resolveFieldAsync(executionContext, parentType, source, entry.getValue())));
        AsyncFieldsCoordinator coordinator = new AsyncFieldsCoordinator(resolvers);
        return new ExecutionResultImpl(serial ? coordinator.executeSerially() : coordinator.executeParallelly(), executionContext.getErrors());
    }

    private CompletionStage<ExecutionResult> resolveFieldAsync(ExecutionContext executionContext, GraphQLObjectType parentType, Object source, List<Field> fieldList) {
        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext.getGraphQLSchema(), parentType, fieldList.get(0));

        DataFetchingEnvironment env = new DataFetchingEnvironment(
          source,
          valuesResolver.getArgumentValues(fieldDef.getArguments(), fieldList.get(0).getArguments(), executionContext.getVariables()),
          executionContext.getRoot(),
          fieldList,
          fieldDef.getType(),
          parentType,
          executionContext.getGraphQLSchema()
        );

        try {
            Object obj1 = fieldDef.getDataFetcher().get(env);
            if (obj1 instanceof CompletionStage) {
                return ((CompletionStage<?>) obj1)
                  .exceptionally(e -> {
                      logExceptionWhileFetching(e, fieldList.get(0));
                      executionContext.addError(new ExceptionWhileDataFetching(e));
                      return null;
                  })
                  .thenCompose(obj2 -> completeValueAsync(executionContext, fieldDef, fieldList, obj2));
            } else {
                return completeValueAsync(executionContext, fieldDef, fieldList, obj1);
            }
        } catch (Exception e) {
            logExceptionWhileFetching(e, fieldList.get(0));
            executionContext.addError(new ExceptionWhileDataFetching(e));
            return completeValueAsync(executionContext, fieldDef, fieldList, null);
        }
    }

    private CompletionStage<ExecutionResult> completeValueAsync(ExecutionContext executionContext, GraphQLFieldDefinition fieldDef, List<Field> fieldList, Object result) {
        ExecutionResult completed = completeValue(executionContext, fieldDef.getType(), fieldList, result);
        // Happens when the data fetcher returns null for nullable field
        if (completed == null) {
            return completedFuture(new ExecutionResultImpl(null, null));
        }
        if (!(completed.getData() instanceof CompletionStage)) {
            return completedFuture(completed);
        }
        return ((CompletionStage<?>) completed.getData()).thenApply(data -> new ExecutionResultImpl(data, completed.getErrors()));
    }

    private void logExceptionWhileFetching(Throwable e, Field field) {
        log.debug("Exception while fetching data for field {}", field.getName(), e);
    }

}
