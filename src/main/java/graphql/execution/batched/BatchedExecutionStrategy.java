package graphql.execution.batched;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import static graphql.execution.FieldCollectorParameters.newParameters;
import static graphql.schema.DataFetchingEnvironmentBuilder.newDataFetchingEnvironment;
import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;

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
        ResultContainer data = ResultContainer.createMapResult(new LinkedHashMap<>(), parameters.source());
        GraphQLObjectType type = parameters.typeInfo().castType(GraphQLObjectType.class);
        GraphQLExecutionNode root = new GraphQLExecutionNode(type, parameters.fields(), singletonList(data));
        return completedFuture(executeImpl(executionContext, parameters, root));
    }

    private ExecutionResult executeImpl(ExecutionContext executionContext, ExecutionStrategyParameters parameters, GraphQLExecutionNode root) {

        Queue<GraphQLExecutionNode> nodes = new ArrayDeque<>();
        nodes.add(root);

        while (!nodes.isEmpty()) {

            GraphQLExecutionNode node = nodes.poll();

            for (String fieldName : node.getFields().keySet()) {

                ExecutionPath fieldPath = parameters.path().segment(fieldName);
                List<Field> currentField = node.getFields().get(fieldName);
                ExecutionStrategyParameters newParameters = parameters
                        .transform(builder -> builder.path(fieldPath).field(currentField));

                List<GraphQLExecutionNode> childNodes = resolveField(executionContext, newParameters, node.getType(),
                        node.getData(), fieldName, currentField);
                nodes.addAll(childNodes);
            }
        }
        return new ExecutionResultImpl(root.getData().get(0).getResult(), executionContext.getErrors());

    }


    private List<GraphQLExecutionNode> resolveField(ExecutionContext executionContext, ExecutionStrategyParameters parameters, GraphQLObjectType parentType,
                                                    List<ResultContainer> nodeData, String fieldName, List<Field> fields) {

        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext.getGraphQLSchema(), parentType, fields.get(0));
        List<FetchedValue> values = fetchData(executionContext, parameters, parentType, nodeData, fields, fieldDef);

        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(
                fieldDef.getArguments(), fields.get(0).getArguments(), executionContext.getVariables());
        return completeValues(executionContext, parentType, values, fieldName, fields, fieldDef.getType(), argumentValues);
    }

    private List<GraphQLExecutionNode> completeValues(ExecutionContext executionContext, GraphQLObjectType type,
                                                      List<FetchedValue> fetchedValues, String fieldName, List<Field> fields,
                                                      GraphQLOutputType outputType, Map<String, Object> argumentValues) {

        GraphQLType fieldType = handleNonNullType(outputType, fetchedValues, type, fields);

        if (isPrimitive(fieldType)) {
            handlePrimitives(fetchedValues, fieldName, fieldType);
            return Collections.emptyList();
        } else if (isObject(fieldType)) {
            return handleObject(executionContext, argumentValues, fetchedValues, fieldName, fields, fieldType);
        } else if (isList(fieldType)) {
            return handleList(executionContext, argumentValues, fetchedValues, fieldName, fields, type, (GraphQLList) fieldType);
        } else {
            throw new IllegalArgumentException("Unrecognized type: " + fieldType);
        }
    }

    @SuppressWarnings("unchecked")
    private List<GraphQLExecutionNode> handleList(ExecutionContext executionContext, Map<String, Object> argumentValues,
                                                  List<FetchedValue> values, String fieldName, List<Field> fields,
                                                  GraphQLObjectType parentType, GraphQLList listType) {

        List<FetchedValue> flattenedValues = new ArrayList<>();

        for (FetchedValue value : values) {
            ResultContainer resultContainer = value.getResultContainer();

            if (value.getValue() == null) {
                resultContainer.putResult(fieldName, null);
                continue;
            }
            ResultContainer listResult = resultContainer.createListResultForField(fieldName);
            for (Object rawValue : (List<Object>) value.getValue()) {
                flattenedValues.add(new FetchedValue(listResult, rawValue));
            }
        }

        GraphQLOutputType subType = (GraphQLOutputType) listType.getWrappedType();
        return completeValues(executionContext, parentType, flattenedValues, fieldName, fields, subType, argumentValues);

    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    private List<GraphQLExecutionNode> handleObject(ExecutionContext executionContext, Map<String, Object> argumentValues,
                                                    List<FetchedValue> values, String fieldName, List<Field> fields, GraphQLType fieldType) {

        // collect list of values by actual type (needed because of interfaces and unions)
        Map<GraphQLObjectType, List<ResultContainer>> resultsByType = new LinkedHashMap<>();

        for (FetchedValue value : values) {
            ResultContainer resultContainer = value.getResultContainer();
            if (value.getValue() == null) {
                resultContainer.putResult(fieldName, null);
            } else {
                ResultContainer childResult = resultContainer.createMapResultForField(fieldName, value.getValue());
                GraphQLObjectType graphQLObjectType = getGraphQLObjectType(executionContext, fields.get(0), fieldType, value.getValue(), argumentValues);
                resultsByType.computeIfAbsent(graphQLObjectType, (type) -> new ArrayList<>());
                resultsByType.get(graphQLObjectType).add(childResult);
            }
        }
        List<GraphQLExecutionNode> childNodes = new ArrayList<>();
        for (GraphQLObjectType type : resultsByType.keySet()) {
            List<ResultContainer> results = resultsByType.get(type);
            Map<String, List<Field>> childFields = getChildFields(executionContext, type, fields);
            childNodes.add(new GraphQLExecutionNode(type, childFields, results));
        }
        return childNodes;
    }


    private GraphQLType handleNonNullType(GraphQLType fieldType, List<FetchedValue> values,
                                          GraphQLObjectType parentType, List<Field> fields) {
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
            value.getResultContainer().putResult(fieldName, coercedValue);
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
    private List<FetchedValue> fetchData(ExecutionContext executionContext, ExecutionStrategyParameters parameters, GraphQLObjectType parentType,
                                         List<ResultContainer> nodeData, List<Field> fields, GraphQLFieldDefinition fieldDef) {

        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(
                fieldDef.getArguments(), fields.get(0).getArguments(), executionContext.getVariables());
        List<Object> sources = new ArrayList<>();
        for (ResultContainer n : nodeData) {
            sources.add(n.getSource());
        }

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

        List<Object> values;
        try {
            values = (List<Object>) getDataFetcher(fieldDef).get(environment);
            if (values == null || values.size() != nodeData.size()) {
                throw new DataFetchingException("BatchedDataFetcher provided invalid number of result values. Affected fields are set to null.");
            }
        } catch (Exception e) {
            values = Collections.nCopies(nodeData.size(), null);

            DataFetcherExceptionHandlerParameters handlerParameters = DataFetcherExceptionHandlerParameters.newExceptionParameters()
                    .executionContext(executionContext)
                    .dataFetchingEnvironment(environment)
                    .argumentValues(argumentValues)
                    .field(fields.get(0))
                    .fieldDefinition(fieldDef)
                    .path(parameters.path())
                    .exception(e)
                    .build();
            dataFetcherExceptionHandler.accept(handlerParameters);
        }

        List<FetchedValue> retVal = new ArrayList<>();
        for (int i = 0; i < nodeData.size(); i++) {
            retVal.add(new FetchedValue(nodeData.get(i), values.get(i)));
        }
        return retVal;
    }

    private BatchedDataFetcher getDataFetcher(GraphQLFieldDefinition fieldDef) {
        DataFetcher supplied = fieldDef.getDataFetcher();
        return batchingFactory.create(supplied);
    }
}
