package graphql.execution.nextgen;


import graphql.Assert;
import graphql.ExceptionWhileDataFetching;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.execution.Async;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.FetchedValue;
import graphql.execution.FetchedValueCreator;
import graphql.execution.MergedField;
import graphql.execution.ResultPath;
import graphql.execution.ValueUnboxer;
import graphql.execution.ValuesResolver;
import graphql.execution.directives.QueryDirectivesImpl;
import graphql.language.Field;
import graphql.normalized.NormalizedField;
import graphql.normalized.NormalizedQueryTree;
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

        Supplier<NormalizedQueryTree> normalizedQuery = executionContext.getNormalizedQueryTree();
        Supplier<NormalizedField> normalisedField = () -> normalizedQuery.get().getNormalizedField(sameFields, executionInfo.getObjectType(), executionInfo.getPath());
        DataFetchingFieldSelectionSet selectionSet = DataFetchingFieldSelectionSetImpl.newCollector(fieldType, normalisedField);

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
        CompletableFuture<Object> fetchedValueCompletableFuture =
                callDataFetcher(codeRegistry, parentType, fieldDef, environment, executionId, path);

        return fetchedValueCompletableFuture.handle((result, ex) -> {
            return FetchedValueCreator.unbox(ValueUnboxer.DEFAULT, (exception, unboxingContext) -> {
                unboxingContext.addError(new ExceptionWhileDataFetching(path, exception, field.getSourceLocation()));
            }, environment, fetchedValueCompletableFuture);
        });
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
}
