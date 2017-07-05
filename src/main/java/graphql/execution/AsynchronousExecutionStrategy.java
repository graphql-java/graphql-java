package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.parameters.FieldFetchParameters;
import graphql.execution.instrumentation.parameters.FieldParameters;
import graphql.language.Field;
import graphql.schema.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static graphql.execution.FieldCollectorParameters.newParameters;
import static graphql.execution.TypeInfo.newTypeInfo;

public class AsynchronousExecutionStrategy extends ExecutionStrategy {

    private static final Logger log = LoggerFactory.getLogger(AsynchronousExecutionStrategy.class);

    @Override
    public ExecutionResult execute(ExecutionContext executionContext,
                    ExecutionParameters parameters) throws NonNullableFieldWasNullException {

        Map<String, List<Field>> fields = parameters.fields();
        Map<String,Object> results = Collections.synchronizedMap(new HashMap<>());
        CompletionStage<Void> future = CompletableFuture.completedFuture(null);

        for (String fieldName : fields.keySet()) {
            final List<Field> fieldList = fields.get(fieldName);
            CompletionStage<ExecutionResult> resolveFieldFuture =
                            resolveFieldAsync(executionContext, parameters, fieldList).
                                            thenApplyAsync(executionResult -> {
                                                if (executionResult != null) {
                                                    results.put(fieldName, executionResult.getData());
                                                } else {
                                                    results.put(fieldName, null);
                                                }
                                                return executionResult;
                                            });
            future = future.thenCombineAsync(resolveFieldFuture,(t,executionResult)-> t);
        }

        return new ExecutionResultImpl(future.thenApplyAsync(t -> results),executionContext.getErrors());
    }

    protected CompletionStage<ExecutionResult> resolveFieldAsync(ExecutionContext executionContext, ExecutionParameters parameters, List<Field> fields) {
        GraphQLObjectType type = parameters.typeInfo().castType(GraphQLObjectType.class);
        GraphQLFieldDefinition
                        fieldDef = getFieldDef(executionContext.getGraphQLSchema(), type, fields.get(0));

        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(fieldDef.getArguments(), fields.get(0).getArguments(), executionContext.getVariables());

        GraphQLOutputType fieldType = fieldDef.getType();
        DataFetchingFieldSelectionSet fieldCollector = DataFetchingFieldSelectionSetImpl
                        .newCollector(executionContext, fieldType, fields);

        DataFetchingEnvironment environment = new DataFetchingEnvironmentImpl(
                        parameters.source(),
                        argumentValues,
                        executionContext.getRoot(),
                        fields,
                        fieldType,
                        type,
                        executionContext.getGraphQLSchema(),
                        executionContext.getFragmentsByName(),
                        executionContext.getExecutionId(),
                        fieldCollector);

        Instrumentation instrumentation = executionContext.getInstrumentation();

        InstrumentationContext<ExecutionResult>
                        fieldCtx = instrumentation.beginField(new FieldParameters(executionContext, fieldDef, environment));

        InstrumentationContext<Object> fetchCtx = instrumentation.beginFieldFetch(new FieldFetchParameters(executionContext, fieldDef, environment));
        Object resolvedValue = null;

        CompletableFuture<Object> dataFetcherResult = null;
        try {
            resolvedValue = fieldDef.getDataFetcher().get(environment);

            if(resolvedValue instanceof CompletionStage) {
                dataFetcherResult = (CompletableFuture) resolvedValue;
            } else {
                dataFetcherResult = CompletableFuture.completedFuture(resolvedValue);
            }
        } catch (Exception e) {
            log.warn("Exception while fetching data", e);
            dataFetcherResult = new CompletableFuture();
            dataFetcherResult.completeExceptionally(e);
        }

        return dataFetcherResult.handleAsync((value,th)-> {
            if(th != null) {
                log.warn("Exception while fetching data", th);
                handleDataFetchingException(executionContext, fieldDef, argumentValues, new ExecutionException(th));
                fetchCtx.onEnd(th);
            }

            TypeInfo fieldTypeInfo = newTypeInfo()
                            .type(fieldType)
                            .parentInfo(parameters.typeInfo())
                            .build();

            ExecutionParameters newParameters = ExecutionParameters.newParameters()
                            .typeInfo(fieldTypeInfo)
                            .fields(parameters.fields())
                            .arguments(argumentValues)
                            .source(value).build();

            return newParameters;
        }).thenComposeAsync(newParameters -> completeValueAsync(executionContext, newParameters,
                        fields));


    }
    protected CompletionStage<ExecutionResult> completeValueAsync(ExecutionContext executionContext, ExecutionParameters parameters, List<Field> fields) {
        TypeInfo typeInfo = parameters.typeInfo();
        Object result = parameters.source();
        GraphQLType fieldType = parameters.typeInfo().type();

        if (result == null) {
            if (typeInfo.typeIsNonNull()) {
                // see http://facebook.github.io/graphql/#sec-Errors-and-Non-Nullability
                NonNullableFieldWasNullException nonNullException = new NonNullableFieldWasNullException(typeInfo);
                executionContext.addError(nonNullException);
                throw nonNullException;
            }
            return CompletableFuture.completedFuture(null);
        } else if (fieldType instanceof GraphQLList) {
            return completeValueForListAsync(executionContext, parameters, fields, toIterable(result));
        } else if (fieldType instanceof GraphQLScalarType) {
            return CompletableFuture.completedFuture(completeValueForScalar((GraphQLScalarType) fieldType, result));
        } else if (fieldType instanceof GraphQLEnumType) {
            return CompletableFuture.completedFuture(completeValueForEnum((GraphQLEnumType) fieldType, result));
        }


        GraphQLObjectType resolvedType;
        if (fieldType instanceof GraphQLInterfaceType) {
            TypeResolutionParameters resolutionParams = TypeResolutionParameters.newParameters()
                            .graphQLInterfaceType((GraphQLInterfaceType) fieldType)
                            .field(fields.get(0))
                            .value(parameters.source())
                            .argumentValues(parameters.arguments())
                            .schema(executionContext.getGraphQLSchema()).build();
            resolvedType = resolveTypeForInterface(resolutionParams);

        } else if (fieldType instanceof GraphQLUnionType) {
            TypeResolutionParameters resolutionParams = TypeResolutionParameters.newParameters()
                            .graphQLUnionType((GraphQLUnionType) fieldType)
                            .field(fields.get(0))
                            .value(parameters.source())
                            .argumentValues(parameters.arguments())
                            .schema(executionContext.getGraphQLSchema()).build();
            resolvedType = resolveTypeForUnion(resolutionParams);
        } else {
            resolvedType = (GraphQLObjectType) fieldType;
        }

        FieldCollectorParameters collectorParameters = newParameters(executionContext.getGraphQLSchema(), resolvedType)
                        .fragments(executionContext.getFragmentsByName())
                        .variables(executionContext.getVariables())
                        .build();

        Map<String, List<Field>> subFields = fieldCollector.collectFields(collectorParameters, fields);

        ExecutionParameters newParameters = ExecutionParameters.newParameters()
                        .typeInfo(typeInfo.asType(resolvedType))
                        .fields(subFields)
                        .source(result).build();

        // Calling this from the executionContext to ensure we shift back from mutation strategy to the query strategy.

        ExecutionResult executionResult = executionContext.getQueryStrategy().execute(executionContext, newParameters);
        if(!(executionResult.getData() instanceof CompletionStage)) {
            return CompletableFuture.completedFuture(executionResult);
        } else {
            return ((CompletionStage) executionResult.getData()).handleAsync((resultMap,th) ->
                            new ExecutionResultImpl(resultMap,executionResult.getErrors())
            );

        }
    }

    protected CompletionStage<ExecutionResult> completeValueForListAsync(ExecutionContext executionContext, ExecutionParameters parameters, List<Field> fields, Iterable<Object> result) {
        TypeInfo typeInfo = parameters.typeInfo();
        GraphQLList fieldType = typeInfo.castType(GraphQLList.class);
        List<Object> resultList = Collections.synchronizedList(new ArrayList<>());
        CompletionStage<Void> future = CompletableFuture.completedFuture(null);

        for (Object item : result) {
            ExecutionParameters newParameters = ExecutionParameters.newParameters()
                            .typeInfo(typeInfo.asType(fieldType.getWrappedType()))
                            .fields(parameters.fields())
                            .source(item).build();

            CompletionStage<ExecutionResult> completedValueFuture =
                            completeValueAsync(executionContext, newParameters, fields);

            future = future.thenCombineAsync(completedValueFuture, (t,executionResult) -> {
                resultList.add(executionResult.getData());
                return null;
            });
        }

        return future.thenApplyAsync(t -> new ExecutionResultImpl(resultList,null));
    }

    private Iterable<Object> toIterable(Object result) {
        if (result.getClass().isArray()) {
            result = Arrays.asList((Object[]) result);
        }
        //noinspection unchecked
        return (Iterable<Object>) result;
    }
}

