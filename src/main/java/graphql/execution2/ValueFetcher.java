package graphql.execution2;


import graphql.Assert;
import graphql.ExceptionWhileDataFetching;
import graphql.GraphQLError;
import graphql.execution.AbsoluteGraphQLError;
import graphql.execution.Async;
import graphql.execution.DataFetcherResult;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionPath;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ValuesResolver;
import graphql.language.Field;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.DataFetchingFieldSelectionSetImpl;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLOutputType;
import graphql.schema.visibility.GraphqlFieldVisibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static graphql.schema.DataFetchingEnvironmentBuilder.newDataFetchingEnvironment;

public class ValueFetcher {

    private final ExecutionContext executionContext;

    ValuesResolver valuesResolver = new ValuesResolver();

    private static final Logger log = LoggerFactory.getLogger(ValueFetcher.class);

    public static final Object NULL_VALUE = new Object();

    public ValueFetcher(ExecutionContext executionContext) {
        this.executionContext = executionContext;
    }


    public CompletableFuture<List<FetchedValue>> fetchBatchedValues(List<Object> sources, List<Field> sameFields, List<ExecutionStepInfo> executionInfos) {
        System.out.println("Fetch batch values size: " + sources.size());
        ExecutionStepInfo executionStepInfo = executionInfos.get(0);
        if (isDataFetcherBatched(sameFields, executionStepInfo)) {
            //TODO: the stepInfo is not correct for all values: how to give the DF all executionInfos?
            return fetchValue(sources, sameFields, executionStepInfo)
                    .thenApply(fetchedValue -> extractBatchedValues(fetchedValue, sources.size()));
        } else {
            List<CompletableFuture<FetchedValue>> fetchedValues = new ArrayList<>();
            for (int i = 0; i < sources.size(); i++) {
                fetchedValues.add(fetchValue(sources.get(i), sameFields, executionInfos.get(i)));
            }
            return Async.each(fetchedValues);
        }
    }

    private List<FetchedValue> extractBatchedValues(FetchedValue fetchedValueContainingList, int expectedSize) {
        List<Object> list = (List<Object>) fetchedValueContainingList.getFetchedValue();
        Assert.assertTrue(list.size() == expectedSize, "Unexpected result size");
        List<FetchedValue> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            List<GraphQLError> errors;
            if (i == 0) {
                errors = fetchedValueContainingList.getErrors();
            } else {
                errors = Collections.emptyList();
            }
            FetchedValue fetchedValue = new FetchedValue(list.get(0), fetchedValueContainingList.getRawFetchedValue(), errors);
            result.add(fetchedValue);
        }
        return result;
    }

    private boolean isDataFetcherBatched(List<Field> sameFields, ExecutionStepInfo executionStepInfo) {
        GraphQLFieldDefinition fieldDef = executionStepInfo.getFieldDefinition();
        return fieldDef.getDataFetcher() instanceof BatchedDataFetcher;
    }

    public CompletableFuture<FetchedValue> fetchValue(Object source, List<Field> sameFields, ExecutionStepInfo executionInfo) {
        Field field = sameFields.get(0);
        GraphQLFieldDefinition fieldDef = executionInfo.getFieldDefinition();

        GraphqlFieldVisibility fieldVisibility = executionContext.getGraphQLSchema().getFieldVisibility();
        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(fieldVisibility, fieldDef.getArguments(), field.getArguments(), executionContext.getVariables());

        GraphQLOutputType fieldType = fieldDef.getType();
        DataFetchingFieldSelectionSet fieldCollector = DataFetchingFieldSelectionSetImpl.newCollector(executionContext, fieldType, sameFields);

        DataFetchingEnvironment environment = newDataFetchingEnvironment(executionContext)
                .source(source)
                .arguments(argumentValues)
                .fieldDefinition(fieldDef)
                .fields(sameFields)
                .fieldType(fieldType)
                .executionStepInfo(executionInfo)
                .parentType(executionInfo.getParent().getType())
                .selectionSet(fieldCollector)
                .build();

        ExecutionId executionId = executionContext.getExecutionId();
        ExecutionPath path = executionInfo.getPath();
        return callDataFetcher(fieldDef, environment, executionId, path)
                .thenApply(rawFetchedValue -> new FetchedValue(rawFetchedValue, rawFetchedValue, Collections.emptyList()))
                .exceptionally(exception -> handleExceptionWhileFetching(field, path, exception))
                .thenApply(result -> unboxPossibleDataFetcherResult(sameFields, path, result))
                .thenApply(this::unboxPossibleOptional);
    }

    private FetchedValue handleExceptionWhileFetching(Field field, ExecutionPath path, Throwable exception) {
        ExceptionWhileDataFetching exceptionWhileDataFetching = new ExceptionWhileDataFetching(path, exception, field.getSourceLocation());
        FetchedValue fetchedValue = new FetchedValue(
                null,
                null,
                Collections.singletonList(exceptionWhileDataFetching));
        return fetchedValue;
    }

    private FetchedValue unboxPossibleOptional(FetchedValue result) {
        return new FetchedValue(UnboxPossibleOptional.unboxPossibleOptional(result.getFetchedValue()), result.getRawFetchedValue(), result.getErrors());

    }

    private CompletableFuture<Object> callDataFetcher(GraphQLFieldDefinition fieldDef, DataFetchingEnvironment environment, ExecutionId executionId, ExecutionPath path) {
        CompletableFuture<Object> result = new CompletableFuture<>();
        try {
            DataFetcher dataFetcher = fieldDef.getDataFetcher();
            log.debug("'{}' fetching field '{}' using data fetcher '{}'...", executionId, path, dataFetcher.getClass().getName());
            Object fetchedValueRaw = dataFetcher.get(environment);
            log.debug("'{}' field '{}' fetch returned '{}'", executionId, path, fetchedValueRaw == null ? "null" : fetchedValueRaw.getClass().getName());
            handleFetchedValue(fetchedValueRaw, result);
        } catch (Exception e) {
            log.debug(String.format("'%s', field '%s' fetch threw exception", executionId, path), e);
            result.completeExceptionally(e);
        }
        return result;
    }

    private void handleFetchedValue(Object fetchedValue, CompletableFuture<Object> cf) {
        if (fetchedValue == null) {
            cf.complete(NULL_VALUE);
            return;
        }
        if (fetchedValue instanceof CompletionStage) {
            ((CompletionStage<Object>) fetchedValue).whenComplete((value, throwable) -> {
                if (throwable != null) {
                    cf.completeExceptionally(throwable);
                } else {
                    cf.complete(value);
                }
            });
            return;
        }
        cf.complete(fetchedValue);

    }

    private FetchedValue unboxPossibleDataFetcherResult(List<Field> sameField, ExecutionPath executionPath, FetchedValue result) {
        if (result.getFetchedValue() instanceof DataFetcherResult) {
            DataFetcherResult<?> dataFetcherResult = (DataFetcherResult) result.getFetchedValue();
            List<AbsoluteGraphQLError> addErrors = dataFetcherResult.getErrors().stream()
                    .map(relError -> new AbsoluteGraphQLError(sameField, executionPath, relError))
                    .collect(Collectors.toList());
            List<GraphQLError> newErrors = new ArrayList<>(result.getErrors());
            newErrors.addAll(addErrors);
            return new FetchedValue(dataFetcherResult.getData(), result.getRawFetchedValue(), newErrors);
        } else {
            return result;
        }
    }


}
