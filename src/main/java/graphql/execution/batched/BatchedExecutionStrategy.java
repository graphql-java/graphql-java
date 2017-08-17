package graphql.execution.batched;

import graphql.Assert;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLException;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionPath;
import graphql.execution.ExecutionStrategy;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.FieldCollectorParameters;
import graphql.execution.SimpleDataFetcherExceptionHandler;
import graphql.execution.TypeResolutionParameters;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

import static graphql.execution.FieldCollectorParameters.newParameters;
import static graphql.schema.DataFetchingEnvironmentBuilder.newDataFetchingEnvironment;
import static java.util.Collections.singletonList;

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
public class BatchedExecutionStrategy extends ExecutionStrategy {

    private static final Logger log = LoggerFactory.getLogger(BatchedExecutionStrategy.class);

    private final BatchedDataFetcherFactory batchingFactory = new BatchedDataFetcherFactory();

    public BatchedExecutionStrategy() {
        this(new SimpleDataFetcherExceptionHandler());
    }

    public BatchedExecutionStrategy(DataFetcherExceptionHandler dataFetcherExceptionHandler) {
        super(dataFetcherExceptionHandler);
    }

    @Override
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        MapOrList data = MapOrList.createMap(new LinkedHashMap<>());
        GraphQLObjectType type = parameters.typeInfo().castType(GraphQLObjectType.class);
        ExecutionNode root = new ExecutionNode(type, parameters.fields(), singletonList(data), Collections.singletonList(parameters.source()));
        Queue<ExecutionNode> nodes = new ArrayDeque<>();
        CompletableFuture<ExecutionResult> result = new CompletableFuture<>();
        executeImpl(executionContext,
                parameters,
                root,
                root,
                nodes,
                root.getFields().keySet().iterator(),
                result);
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

        ExecutionPath fieldPath = parameters.path().segment(fieldName);
        List<Field> currentField = curNode.getFields().get(fieldName);
        ExecutionStrategyParameters newParameters = parameters
                .transform(builder -> builder.path(fieldPath).field(currentField));

        ExecutionNode finalCurNode = curNode;
        Iterator<String> finalCurFieldNames = curFieldNames;
        resolveField(executionContext, newParameters, fieldName, curNode).whenComplete((childNodes, exception) -> {
            if (exception != null) {
                overallResult.completeExceptionally(exception);
                return;
            }
            nodes.addAll(childNodes);
            executeImpl(executionContext, newParameters, root, finalCurNode, nodes, finalCurFieldNames, overallResult);
        });

    }


    private CompletableFuture<List<ExecutionNode>> resolveField(ExecutionContext executionContext, ExecutionStrategyParameters parameters, String fieldName, ExecutionNode node) {
        GraphQLObjectType parentType = node.getType();
        List<Field> fields = node.getFields().get(fieldName);
        List<MapOrList> parentResults = node.getParentResults();

        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext.getGraphQLSchema(), parentType, fields.get(0));
        return fetchData(executionContext, parameters, parentType, parentResults, node.getSources(), fields, fieldDef).thenApply((fetchedValues) -> {
            Map<String, Object> argumentValues = valuesResolver.getArgumentValues(
                    fieldDef.getArguments(), fields.get(0).getArguments(), executionContext.getVariables());
            return completeValues(executionContext, parentType, fetchedValues, fieldName, fields, fieldDef.getType(), argumentValues);
        });

    }

    private List<ExecutionNode> completeValues(ExecutionContext executionContext, GraphQLObjectType parentType,
                                               List<FetchedValue> fetchedValues, String fieldName, List<Field> fields,
                                               GraphQLOutputType fieldType, Map<String, Object> argumentValues) {

        GraphQLType unwrappedFieldType = handleNonNullType(fieldType, fetchedValues, parentType, fields);

        if (isPrimitive(unwrappedFieldType)) {
            handlePrimitives(fetchedValues, fieldName, unwrappedFieldType);
            return Collections.emptyList();
        } else if (isObject(unwrappedFieldType)) {
            return handleObject(executionContext, argumentValues, fetchedValues, fieldName, fields, unwrappedFieldType);
        } else if (isList(unwrappedFieldType)) {
            return handleList(executionContext, argumentValues, fetchedValues, fieldName, fields, parentType, (GraphQLList) unwrappedFieldType);
        } else {
            return Assert.assertShouldNeverHappen("can't handle type: " + unwrappedFieldType);
        }
    }

    @SuppressWarnings("unchecked")
    private List<ExecutionNode> handleList(ExecutionContext executionContext, Map<String, Object> argumentValues,
                                           List<FetchedValue> values, String fieldName, List<Field> fields,
                                           GraphQLObjectType parentType, GraphQLList listType) {

        List<FetchedValue> flattenedValues = new ArrayList<>();

        for (FetchedValue value : values) {
            MapOrList mapOrList = value.getParentResult();

            if (value.getValue() == null) {
                mapOrList.putOrAdd(fieldName, null);
                continue;
            }

            MapOrList listResult = mapOrList.createAndPutList(fieldName);
            for (Object rawValue : (List<Object>) value.getValue()) {
                flattenedValues.add(new FetchedValue(listResult, rawValue));
            }
        }
        GraphQLOutputType subType = (GraphQLOutputType) listType.getWrappedType();
        return completeValues(executionContext, parentType, flattenedValues, fieldName, fields, subType, argumentValues);
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    private List<ExecutionNode> handleObject(ExecutionContext executionContext, Map<String, Object> argumentValues,
                                             List<FetchedValue> values, String fieldName, List<Field> fields, GraphQLType fieldType) {

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
            resultsByType.computeIfAbsent(graphQLObjectType, (type) -> new ArrayList<>());
            resultsByType.get(graphQLObjectType).add(childResult);

            sourceByType.computeIfAbsent(graphQLObjectType, (type) -> new ArrayList<>());
            sourceByType.get(graphQLObjectType).add(value.getValue());
        }

        List<ExecutionNode> childNodes = new ArrayList<>();
        for (GraphQLObjectType type : resultsByType.keySet()) {
            List<MapOrList> results = resultsByType.get(type);
            List<Object> sources = sourceByType.get(type);
            Map<String, List<Field>> childFields = getChildFields(executionContext, type, fields);
            childNodes.add(new ExecutionNode(type, childFields, results, sources));
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

    @SuppressWarnings("unchecked")
    private CompletableFuture<List<FetchedValue>> fetchData(ExecutionContext executionContext, ExecutionStrategyParameters parameters, GraphQLObjectType parentType,
                                                            List<MapOrList> parentResults, List<Object> sources, List<Field> fields, GraphQLFieldDefinition fieldDef) {

        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(
                fieldDef.getArguments(), fields.get(0).getArguments(), executionContext.getVariables());

        GraphQLOutputType fieldType = fieldDef.getType();
        DataFetchingFieldSelectionSet fieldCollector = DataFetchingFieldSelectionSetImpl.newCollector(executionContext, fieldType, fields);

        DataFetchingEnvironment environment = newDataFetchingEnvironment(executionContext)
                .source(sources)
                .arguments(argumentValues)
                .fieldDefinition(fieldDef)
                .fields(fields)
                .fieldType(fieldDef.getType())
                .fieldTypeInfo(parameters.typeInfo())
                .parentType(parentType)
                .selectionSet(fieldCollector)
                .build();

        CompletableFuture<Object> valuesFuture;
        try {
            Object rawValue = getDataFetcher(fieldDef).get(environment);
            if (rawValue instanceof CompletionStage) {
                valuesFuture = ((CompletionStage) rawValue).toCompletableFuture();
            } else {
                valuesFuture = CompletableFuture.completedFuture(rawValue);
            }
        } catch (Exception e) {
            valuesFuture = new CompletableFuture<>();
            valuesFuture.completeExceptionally(e);
        }
        return valuesFuture
                .thenApply((result) -> assertResult(parentResults, result))
                .handle(handleResult(executionContext, parameters, parentResults, fields, fieldDef, argumentValues, environment));
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
                retVal.add(new FetchedValue(parentResults.get(i), values.get(i)));
            }
            return retVal;
        };
    }

    private List<Object> assertResult(List<MapOrList> parentResults, Object result) {
        if (result != null && !(result instanceof List)) {
            throw new DataFetchingException("invalid result from DataFetcher: List expected");
        }
        List<Object> values = (List<Object>) result;
        if (values == null || values.size() != parentResults.size()) {
            throw new DataFetchingException("BatchedDataFetcher provided invalid number of result values. Affected fields are set to null.");
        }
        return values;
    }

    private BatchedDataFetcher getDataFetcher(GraphQLFieldDefinition fieldDef) {
        DataFetcher supplied = fieldDef.getDataFetcher();
        return batchingFactory.create(supplied);
    }
}
