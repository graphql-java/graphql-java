package graphql.execution.nextgen;


import com.google.common.collect.ImmutableList;
import graphql.Assert;
import graphql.ExceptionWhileDataFetching;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.collect.ImmutableKit;
import graphql.execution.Async;
import graphql.execution.DataFetcherResult;
import graphql.execution.DefaultValueUnboxer;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.FetchedValue;
import graphql.execution.MergedField;
import graphql.execution.ResultPath;
import graphql.execution.ValuesResolver;
import graphql.execution.directives.QueryDirectivesImpl;
import graphql.language.Field;
import graphql.normalized.ExecutableNormalizedField;
import graphql.normalized.ExecutableNormalizedOperation;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.DataFetchingFieldSelectionSetImpl;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLTypeUtil;
import graphql.util.FpKit;
import graphql.util.LogKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static graphql.schema.DataFetchingEnvironmentImpl.newDataFetchingEnvironment;
import static java.util.Collections.singletonList;

/**
 * @deprecated Jan 2022 - We have decided to deprecate the NextGen engine, and it will be removed in a future release.
 */
@Deprecated
@Internal
public class ValueFetcher {


    ValuesResolver valuesResolver = new ValuesResolver();

    private static final Logger log = LoggerFactory.getLogger(ValueFetcher.class);
    private static final Logger logNotSafe = LogKit.getNotPrivacySafeLogger(ExecutionStrategy.class);

    public static final Object NULL_VALUE = new Object();

    public ValueFetcher() {
    }


    public CompletableFuture<List<FetchedValue>> fetchBatchedValues(ExecutionContext executionContext, List<Object> sources, MergedField field, List<ExecutionStepInfo> executionInfos) {
        ExecutionStepInfo executionStepInfo = executionInfos.get(0);
        // TODO - add support for field context to batching code
        Object todoLocalContext = null;
        if (isDataFetcherBatched(executionContext, executionStepInfo)) {
            return fetchValue(executionContext, sources, todoLocalContext, field, executionStepInfo)
                    .thenApply(fetchedValue -> extractBatchedValues(fetchedValue, sources.size()));
        } else {
            List<CompletableFuture<FetchedValue>> fetchedValues = new ArrayList<>();
            for (int i = 0; i < sources.size(); i++) {
                fetchedValues.add(fetchValue(executionContext, sources.get(i), todoLocalContext, field, executionInfos.get(i)));
            }
            return Async.each(fetchedValues);
        }
    }

    @SuppressWarnings("unchecked")
    private List<FetchedValue> extractBatchedValues(FetchedValue fetchedValueContainingList, int expectedSize) {
        List<Object> list = (List<Object>) fetchedValueContainingList.getFetchedValue();
        Assert.assertTrue(list.size() == expectedSize, () -> "Unexpected result size");
        List<FetchedValue> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            List<GraphQLError> errors;
            if (i == 0) {
                errors = fetchedValueContainingList.getErrors();
            } else {
                errors = Collections.emptyList();
            }
            FetchedValue fetchedValue = FetchedValue.newFetchedValue()
                    .fetchedValue(list.get(i))
                    .rawFetchedValue(fetchedValueContainingList.getRawFetchedValue())
                    .errors(errors)
                    .localContext(fetchedValueContainingList.getLocalContext())
                    .build();
            result.add(fetchedValue);
        }
        return result;
    }

    private GraphQLFieldsContainer getFieldsContainer(ExecutionStepInfo executionStepInfo) {
        GraphQLOutputType type = executionStepInfo.getParent().getType();
        return (GraphQLFieldsContainer) GraphQLTypeUtil.unwrapAll(type);
    }

    private boolean isDataFetcherBatched(ExecutionContext executionContext, ExecutionStepInfo executionStepInfo) {
        GraphQLFieldsContainer parentType = getFieldsContainer(executionStepInfo);
        GraphQLFieldDefinition fieldDef = executionStepInfo.getFieldDefinition();
        DataFetcher dataFetcher = executionContext.getGraphQLSchema().getCodeRegistry().getDataFetcher(parentType, fieldDef);
        return dataFetcher instanceof BatchedDataFetcher;
    }

    public CompletableFuture<FetchedValue> fetchValue(ExecutionContext executionContext, Object source, Object localContext, MergedField sameFields, ExecutionStepInfo executionInfo) {
        Field field = sameFields.getSingleField();
        GraphQLFieldDefinition fieldDef = executionInfo.getFieldDefinition();

        GraphQLCodeRegistry codeRegistry = executionContext.getGraphQLSchema().getCodeRegistry();
        GraphQLFieldsContainer parentType = getFieldsContainer(executionInfo);

        Supplier<Map<String, Object>> argumentValues = FpKit.intraThreadMemoize(() -> valuesResolver.getArgumentValues(codeRegistry, fieldDef.getArguments(), field.getArguments(), executionContext.getVariables()));

        QueryDirectivesImpl queryDirectives = new QueryDirectivesImpl(sameFields, executionContext.getGraphQLSchema(), executionContext.getVariables());

        GraphQLOutputType fieldType = fieldDef.getType();

        Supplier<ExecutableNormalizedOperation> normalizedQuery = executionContext.getNormalizedQueryTree();
        Supplier<ExecutableNormalizedField> normalisedField = () -> normalizedQuery.get().getNormalizedField(sameFields, executionInfo.getObjectType(), executionInfo.getPath());
        DataFetchingFieldSelectionSet selectionSet = DataFetchingFieldSelectionSetImpl.newCollector(executionContext.getGraphQLSchema(), fieldType, normalisedField);

        DataFetchingEnvironment environment = newDataFetchingEnvironment(executionContext)
                .source(source)
                .localContext(localContext)
                .arguments(argumentValues)
                .fieldDefinition(fieldDef)
                .mergedField(sameFields)
                .fieldType(fieldType)
                .executionStepInfo(executionInfo)
                .parentType(parentType)
                .selectionSet(selectionSet)
                .queryDirectives(queryDirectives)
                .build();

        ExecutionId executionId = executionContext.getExecutionId();
        ResultPath path = executionInfo.getPath();
        return callDataFetcher(codeRegistry, parentType, fieldDef, environment, executionId, path)
                .thenApply(rawFetchedValue -> FetchedValue.newFetchedValue()
                        .fetchedValue(rawFetchedValue)
                        .rawFetchedValue(rawFetchedValue)
                        .build())
                .exceptionally(exception -> handleExceptionWhileFetching(field, path, exception))
                .thenApply(result -> unboxPossibleDataFetcherResult(sameFields, path, result, localContext))
                .thenApply(this::unboxPossibleOptional);
    }

    private FetchedValue handleExceptionWhileFetching(Field field, ResultPath path, Throwable exception) {
        ExceptionWhileDataFetching exceptionWhileDataFetching = new ExceptionWhileDataFetching(path, exception, field.getSourceLocation());
        return FetchedValue.newFetchedValue().errors(singletonList(exceptionWhileDataFetching)).build();
    }

    private FetchedValue unboxPossibleOptional(FetchedValue result) {
        return result.transform(
                builder -> builder.fetchedValue(DefaultValueUnboxer.unboxValue(result.getFetchedValue()))
        );
    }

    private CompletableFuture<Object> callDataFetcher(GraphQLCodeRegistry codeRegistry, GraphQLFieldsContainer parentType, GraphQLFieldDefinition fieldDef, DataFetchingEnvironment environment, ExecutionId executionId, ResultPath path) {
        CompletableFuture<Object> result = new CompletableFuture<>();
        try {
            DataFetcher dataFetcher = codeRegistry.getDataFetcher(parentType, fieldDef);
            if (log.isDebugEnabled()) {
                log.debug("'{}' fetching field '{}' using data fetcher '{}'...", executionId, path, dataFetcher.getClass().getName());
            }
            Object fetchedValueRaw = dataFetcher.get(environment);
            if (logNotSafe.isDebugEnabled()) {
                logNotSafe.debug("'{}' field '{}' fetch returned '{}'", executionId, path, fetchedValueRaw == null ? "null" : fetchedValueRaw.getClass().getName());
            }
            handleFetchedValue(fetchedValueRaw, result);
        } catch (Exception e) {
            if (logNotSafe.isDebugEnabled()) {
                logNotSafe.debug("'{}', field '{}' fetch threw exception", executionId, path, e);
            }
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
            //noinspection unchecked
            CompletionStage<Object> stage = (CompletionStage<Object>) fetchedValue;
            stage.whenComplete((value, throwable) -> {
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

    private FetchedValue unboxPossibleDataFetcherResult(MergedField sameField, ResultPath resultPath, FetchedValue result, Object localContext) {
        if (result.getFetchedValue() instanceof DataFetcherResult) {

            DataFetcherResult<?> dataFetcherResult = (DataFetcherResult) result.getFetchedValue();
            List<GraphQLError> addErrors = ImmutableList.copyOf(dataFetcherResult.getErrors());
            List<GraphQLError> newErrors = ImmutableKit.concatLists(result.getErrors(), addErrors);

            Object newLocalContext = dataFetcherResult.getLocalContext();
            if (newLocalContext == null) {
                // if the field returns nothing then they get the context of their parent field
                newLocalContext = localContext;
            }
            return FetchedValue.newFetchedValue()
                    .fetchedValue(dataFetcherResult.getData())
                    .rawFetchedValue(result.getRawFetchedValue())
                    .errors(newErrors)
                    .localContext(newLocalContext)
                    .build();
        } else {
            return result;
        }
    }
}
