package graphql.execution.batched;

import graphql.Assert;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLException;
import graphql.PublicApi;
import graphql.execution.Async;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionPath;
import graphql.execution.ExecutionStrategy;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.ExecutionTypeInfo;
import graphql.execution.FieldCollectorParameters;
import graphql.execution.SimpleDataFetcherExceptionHandler;
import graphql.execution.TypeResolutionParameters;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.language.Field;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.DataFetchingFieldSelectionSetImpl;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;

import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

import static graphql.execution.ExecutionTypeInfo.newTypeInfo;
import static graphql.execution.FieldCollectorParameters.newParameters;
import static graphql.schema.DataFetchingEnvironmentBuilder.newDataFetchingEnvironment;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * Execution Strategy that minimizes calls to the data fetcher when used in conjunction with {@link DataFetcher}s that have
 * {@link DataFetcher#get(DataFetchingEnvironment)} methods annotated with {@link Batched}. See the javadoc comment on
 * {@link Batched} for a more detailed description of batched data fetchers.
 * <p>
 * The strategy runs a BFS over terms of the query and passes a list of all the relevant sources to the batched data fetcher.
 * </p>
 * Normal DataFetchers can be used, however they will not see benefits of batching as they expect a single source object
 * at a time.
 */
@PublicApi
public class BatchedExecutionStrategy extends ExecutionStrategy {

    private final BatchedDataFetcherFactory batchingFactory = new BatchedDataFetcherFactory();

    public BatchedExecutionStrategy() {
        this(new SimpleDataFetcherExceptionHandler());
    }

    public BatchedExecutionStrategy(DataFetcherExceptionHandler dataFetcherExceptionHandler) {
        super(dataFetcherExceptionHandler);
    }

    @Override
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        InstrumentationContext<CompletableFuture<ExecutionResult>> executionStrategyCtx = executionContext.getInstrumentation().beginExecutionStrategy(new InstrumentationExecutionStrategyParameters(executionContext));

        GraphQLObjectType type = parameters.typeInfo().castType(GraphQLObjectType.class);

        ExecutionNode root = new ExecutionNode(type,
                parameters.typeInfo(),
                parameters.fields(),
                singletonList(MapOrList.createMap(new LinkedHashMap<>())),
                Collections.singletonList(parameters.source())
        );

        Queue<ExecutionNode> nodes = new ArrayDeque<>();
        CompletableFuture<ExecutionResult> result = new CompletableFuture<>();
        executeImpl(executionContext,
                parameters,
                root,
                root,
                nodes,
                root.getFields().keySet().iterator(),
                result);

        executionStrategyCtx.onEnd(result, null);
        return result;
    }

    private void executeImpl(ExecutionContext executionContext,
                             ExecutionStrategyParameters parameters,
                             ExecutionNode root,
                             ExecutionNode curNode,
                             Queue<ExecutionNode> nodes,
                             Iterator<String> curFieldNames,
                             CompletableFuture<ExecutionResult> overallResult) {

        if (!curFieldNames.hasNext() && nodes.isEmpty()) {
            overallResult.complete(new ExecutionResultImpl(root.getParentResults().get(0).toObject(), executionContext.getErrors()));
            return;
        }

        if (!curFieldNames.hasNext()) {
            curNode = nodes.poll();
            curFieldNames = curNode.getFields().keySet().iterator();
        }

        String fieldName = curFieldNames.next();
        List<Field> currentField = curNode.getFields().get(fieldName);


        //
        // once an object is resolved from a interface / union to a node with an object type, the
        // parent type info has effectively changed (it has got more specific), even though the path etc...
        // has not changed
        ExecutionTypeInfo currentParentTypeInfo = parameters.typeInfo();
        ExecutionTypeInfo newParentTypeInfo = newTypeInfo()
                .type(curNode.getType())
                .fieldDefinition(currentParentTypeInfo.getFieldDefinition())
                .path(currentParentTypeInfo.getPath())
                .parentInfo(currentParentTypeInfo.getParentTypeInfo())
                .build();

        ExecutionPath fieldPath = curNode.getTypeInfo().getPath().segment(fieldName);
        GraphQLFieldDefinition fieldDefinition = getFieldDef(executionContext.getGraphQLSchema(), curNode.getType(), currentField.get(0));

        ExecutionTypeInfo typeInfo = newTypeInfo()
                .type(fieldDefinition.getType())
                .fieldDefinition(fieldDefinition)
                .path(fieldPath)
                .parentInfo(newParentTypeInfo)
                .build();

        ExecutionStrategyParameters newParameters = parameters
                .transform(builder -> builder
                        .path(fieldPath)
                        .field(currentField)
                        .typeInfo(typeInfo)
                );

        ExecutionNode finalCurNode = curNode;
        Iterator<String> finalCurFieldNames = curFieldNames;

        resolveField(executionContext, newParameters, fieldName, curNode)
                .whenComplete((childNodes, exception) -> {
                    if (exception != null) {
                        overallResult.completeExceptionally(exception);
                        return;
                    }
                    nodes.addAll(childNodes);
                    executeImpl(executionContext, newParameters, root, finalCurNode, nodes, finalCurFieldNames, overallResult);
                });
    }


    private CompletableFuture<List<ExecutionNode>> resolveField(ExecutionContext executionContext,
                                                                ExecutionStrategyParameters parameters,
                                                                String fieldName,
                                                                ExecutionNode node) {
        GraphQLObjectType parentType = node.getType();
        List<Field> fields = node.getFields().get(fieldName);

        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext.getGraphQLSchema(), parentType, fields.get(0));

        Instrumentation instrumentation = executionContext.getInstrumentation();
        ExecutionTypeInfo typeInfo = parameters.typeInfo();
        InstrumentationContext<ExecutionResult> fieldCtx = instrumentation.beginField(
                new InstrumentationFieldParameters(executionContext, fieldDef, typeInfo)
        );

        CompletableFuture<List<FetchedValue>> fetchedData = fetchData(executionContext, parameters, fieldName, node, fieldDef);

        CompletableFuture<List<ExecutionNode>> result = fetchedData.thenApply((fetchedValues) -> {

            Map<String, Object> argumentValues = valuesResolver.getArgumentValues(
                    fieldDef.getArguments(), fields.get(0).getArguments(), executionContext.getVariables());

            return completeValues(executionContext, parentType, fetchedValues, fieldName, fields, fieldDef.getType(), typeInfo, argumentValues);
        });
        result.whenComplete((nodes, throwable) -> fieldCtx.onEnd(null, throwable));
        return result;

    }

    private CompletableFuture<List<FetchedValue>> fetchData(ExecutionContext executionContext,
                                                            ExecutionStrategyParameters parameters,
                                                            String fieldName,
                                                            ExecutionNode node,
                                                            GraphQLFieldDefinition fieldDef) {
        GraphQLObjectType parentType = node.getType();
        List<Field> fields = node.getFields().get(fieldName);
        List<MapOrList> parentResults = node.getParentResults();

        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(
                fieldDef.getArguments(), fields.get(0).getArguments(), executionContext.getVariables());

        GraphQLOutputType fieldType = fieldDef.getType();
        DataFetchingFieldSelectionSet fieldCollector = DataFetchingFieldSelectionSetImpl.newCollector(executionContext, fieldType, fields);

        DataFetchingEnvironment environment = newDataFetchingEnvironment(executionContext)
                .source(node.getSources())
                .arguments(argumentValues)
                .fieldDefinition(fieldDef)
                .fields(fields)
                .fieldType(fieldDef.getType())
                .fieldTypeInfo(parameters.typeInfo())
                .parentType(parentType)
                .selectionSet(fieldCollector)
                .build();

        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationContext<Object> fetchCtx = instrumentation.beginFieldFetch(
                new InstrumentationFieldFetchParameters(executionContext, fieldDef, environment)
        );

        CompletableFuture<Object> fetchedValue;
        try {
            BatchedDataFetcher dataFetcher = getDataFetcher(fieldDef);
            Object fetchedValueRaw = dataFetcher.get(environment);
            fetchedValue = Async.toCompletableFuture(fetchedValueRaw);
        } catch (Exception e) {
            fetchedValue = new CompletableFuture<>();
            fetchedValue.completeExceptionally(e);
        }
        return fetchedValue
                .thenApply((result) -> assertResult(parentResults, result))
                .whenComplete(fetchCtx::onEnd)
                .handle(handleResult(executionContext, parameters, parentResults, fields, fieldDef, argumentValues, environment));
    }

    private List<ExecutionNode> completeValues(ExecutionContext executionContext, GraphQLObjectType parentType,
                                               List<FetchedValue> fetchedValues, String fieldName, List<Field> fields,
                                               GraphQLOutputType fieldType, ExecutionTypeInfo typeInfo, Map<String, Object> argumentValues) {

        GraphQLType unwrappedFieldType = handleNonNullType(fieldType, fetchedValues, parentType, fields);

        if (isPrimitive(unwrappedFieldType)) {
            handlePrimitives(fetchedValues, fieldName, unwrappedFieldType);
            return Collections.emptyList();
        } else if (isObject(unwrappedFieldType)) {
            return handleObject(executionContext, argumentValues, fetchedValues, fieldName, fields, unwrappedFieldType, typeInfo);
        } else if (isList(unwrappedFieldType)) {
            return handleList(executionContext, argumentValues, fetchedValues, fieldName, fields, parentType, typeInfo, (GraphQLList) unwrappedFieldType);
        } else {
            return Assert.assertShouldNeverHappen("can't handle type: " + unwrappedFieldType);
        }
    }

    @SuppressWarnings("unchecked")
    private List<ExecutionNode> handleList(ExecutionContext executionContext, Map<String, Object> argumentValues,
                                           List<FetchedValue> values, String fieldName, List<Field> fields,
                                           GraphQLObjectType parentType, ExecutionTypeInfo typeInfo, GraphQLList listType) {

        List<FetchedValue> flattenedValues = new ArrayList<>();

        for (FetchedValue value : values) {
            MapOrList mapOrList = value.getParentResult();

            if (value.getValue() == null) {
                mapOrList.putOrAdd(fieldName, null);
                continue;
            }

            MapOrList listResult = mapOrList.createAndPutList(fieldName);
            for (Object rawValue : toIterable(value.getValue())) {
                rawValue = unboxPossibleOptional(rawValue);
                flattenedValues.add(new FetchedValue(listResult, rawValue));
            }
        }
        GraphQLOutputType subType = (GraphQLOutputType) listType.getWrappedType();
        return completeValues(executionContext, parentType, flattenedValues, fieldName, fields, subType, typeInfo, argumentValues);
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    private List<ExecutionNode> handleObject(ExecutionContext executionContext, Map<String, Object> argumentValues,
                                             List<FetchedValue> values, String fieldName, List<Field> fields, GraphQLType fieldType,
                                             ExecutionTypeInfo typeInfo) {

        // collect list of values by actual type (needed because of interfaces and unions)
        Map<GraphQLObjectType, List<MapOrList>> resultsByType = new LinkedHashMap<>();
        Map<GraphQLObjectType, List<Object>> sourceByType = new LinkedHashMap<>();

        for (FetchedValue value : values) {
            MapOrList mapOrList = value.getParentResult();
            if (value.getValue() == null) {
                mapOrList.putOrAdd(fieldName, null);
                continue;
            }
            MapOrList childResult = mapOrList.createAndPutMap(fieldName);

            GraphQLObjectType graphQLObjectType = getGraphQLObjectType(executionContext, fields.get(0), fieldType, value.getValue(), argumentValues);
            resultsByType.putIfAbsent(graphQLObjectType, new ArrayList<>());
            resultsByType.get(graphQLObjectType).add(childResult);

            sourceByType.putIfAbsent(graphQLObjectType, new ArrayList<>());
            sourceByType.get(graphQLObjectType).add(value.getValue());
        }

        List<ExecutionNode> childNodes = new ArrayList<>();
        for (GraphQLObjectType type : resultsByType.keySet()) {
            List<MapOrList> results = resultsByType.get(type);
            List<Object> sources = sourceByType.get(type);
            Map<String, List<Field>> childFields = getChildFields(executionContext, type, fields);
            childNodes.add(new ExecutionNode(type, typeInfo, childFields, results, sources));
        }
        return childNodes;
    }


    private GraphQLType handleNonNullType(GraphQLType fieldType, List<FetchedValue> values,
                                          GraphQLObjectType parentType, List<Field> fields) {
        // TODO: check that
        if (isNonNull(fieldType)) {
            for (FetchedValue value : values) {
                if (value.getValue() == null) {
                    throw new GraphQLException("Found null value for non-null type with parent: '"
                            + parentType.getName() + "' for fields: " + fields);
                }
            }
            while (isNonNull(fieldType)) {
                fieldType = ((GraphQLNonNull) fieldType).getWrappedType();
            }
        }
        return fieldType;
    }

    private boolean isNonNull(GraphQLType fieldType) {
        return fieldType instanceof GraphQLNonNull;
    }

    private Map<String, List<Field>> getChildFields(ExecutionContext executionContext, GraphQLObjectType resolvedType,
                                                    List<Field> fields) {

        FieldCollectorParameters collectorParameters = newParameters()
                .schema(executionContext.getGraphQLSchema())
                .objectType(resolvedType)
                .fragments(executionContext.getFragmentsByName())
                .variables(executionContext.getVariables())
                .build();

        return fieldCollector.collectFields(collectorParameters, fields);
    }

    private GraphQLObjectType getGraphQLObjectType(ExecutionContext executionContext, Field field, GraphQLType fieldType, Object value, Map<String, Object> argumentValues) {
        GraphQLObjectType resolvedType = null;
        if (fieldType instanceof GraphQLInterfaceType) {
            resolvedType = resolveTypeForInterface(TypeResolutionParameters.newParameters()
                    .graphQLInterfaceType((GraphQLInterfaceType) fieldType)
                    .field(field)
                    .value(value)
                    .argumentValues(argumentValues)
                    .schema(executionContext.getGraphQLSchema())
                    .build());
        } else if (fieldType instanceof GraphQLUnionType) {
            resolvedType = resolveTypeForUnion(TypeResolutionParameters.newParameters()
                    .graphQLUnionType((GraphQLUnionType) fieldType)
                    .field(field)
                    .value(value)
                    .argumentValues(argumentValues)
                    .schema(executionContext.getGraphQLSchema())
                    .build());
        } else if (fieldType instanceof GraphQLObjectType) {
            resolvedType = (GraphQLObjectType) fieldType;
        }
        return resolvedType;
    }

    private void handlePrimitives(List<FetchedValue> fetchedValues, String fieldName,
                                  GraphQLType type) {
        for (FetchedValue value : fetchedValues) {
            Object coercedValue = coerce(type, value.getValue());
            //6.6.1 http://facebook.github.io/graphql/#sec-Field-entries
            if (coercedValue instanceof Double && ((Double) coercedValue).isNaN()) {
                coercedValue = null;
            }
            value.getParentResult().putOrAdd(fieldName, coercedValue);
        }
    }

    private Object coerce(GraphQLType type, Object value) {
        if (value == null) return null;
        if (type instanceof GraphQLEnumType) {
            return ((GraphQLEnumType) type).getCoercing().serialize(value);
        } else {
            return ((GraphQLScalarType) type).getCoercing().serialize(value);
        }
    }

    private boolean isList(GraphQLType type) {
        return type instanceof GraphQLList;
    }

    private boolean isPrimitive(GraphQLType type) {
        return type instanceof GraphQLScalarType || type instanceof GraphQLEnumType;
    }

    private boolean isObject(GraphQLType type) {
        return type instanceof GraphQLObjectType ||
                type instanceof GraphQLInterfaceType ||
                type instanceof GraphQLUnionType;
    }

    private BiFunction<List<Object>, Throwable, List<FetchedValue>> handleResult(ExecutionContext executionContext, ExecutionStrategyParameters parameters, List<MapOrList> parentResults, List<Field> fields, GraphQLFieldDefinition fieldDef, Map<String, Object> argumentValues, DataFetchingEnvironment environment) {
        return (result, exception) -> {
            if (exception != null) {
                if (exception instanceof CompletionException) {
                    exception = exception.getCause();
                }
                DataFetcherExceptionHandlerParameters handlerParameters = DataFetcherExceptionHandlerParameters.newExceptionParameters()
                        .executionContext(executionContext)
                        .dataFetchingEnvironment(environment)
                        .argumentValues(argumentValues)
                        .field(fields.get(0))
                        .fieldDefinition(fieldDef)
                        .path(parameters.path())
                        .exception(exception)
                        .build();
                dataFetcherExceptionHandler.accept(handlerParameters);
                result = Collections.nCopies(parentResults.size(), null);
            }
            List<Object> values = result;
            List<FetchedValue> retVal = new ArrayList<>();
            for (int i = 0; i < parentResults.size(); i++) {
                Object value = unboxPossibleOptional(values.get(i));
                retVal.add(new FetchedValue(parentResults.get(i), value));
            }
            return retVal;
        };
    }

    private List<Object> assertResult(List<MapOrList> parentResults, Object result) {
        result = convertPossibleArray(result);
        if (result != null && !(result instanceof Iterable)) {
            throw new BatchAssertionFailed(String.format("BatchedDataFetcher provided an invalid result: Iterable expected but got '%s'. Affected fields are set to null.", result.getClass().getName()));
        }
        @SuppressWarnings("unchecked")
        Iterable<Object> iterableResult = (Iterable<Object>) result;
        if (iterableResult == null) {
            throw new BatchAssertionFailed("BatchedDataFetcher provided a null Iterable of result values. Affected fields are set to null.");
        }
        List<Object> resultList = new ArrayList<>();
        iterableResult.forEach(resultList::add);

        long size = resultList.size();
        if (size != parentResults.size()) {
            throw new BatchAssertionFailed(String.format("BatchedDataFetcher provided invalid number of result values, expected %d but got %d. Affected fields are set to null.", parentResults.size(), size));
        }
        return resultList;
    }

    private Object convertPossibleArray(Object result) {
        if (result != null && result.getClass().isArray()) {
            return IntStream.range(0, Array.getLength(result))
                    .mapToObj(i -> Array.get(result, i))
                    .collect(toList());
        }
        return result;
    }

    private BatchedDataFetcher getDataFetcher(GraphQLFieldDefinition fieldDef) {
        DataFetcher supplied = fieldDef.getDataFetcher();
        return batchingFactory.create(supplied);
    }
}
