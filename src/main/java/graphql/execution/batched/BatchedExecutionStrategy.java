package graphql.execution.batched;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLException;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategy;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.FieldCollectorParameters;
import graphql.execution.TypeResolutionParameters;
import graphql.language.Field;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
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

import static graphql.execution.FieldCollectorParameters.newParameters;
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

    @Override
    public ExecutionResult execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        GraphQLExecutionNodeDatum data = new GraphQLExecutionNodeDatum(new LinkedHashMap<>(), parameters.source());
        GraphQLObjectType type = parameters.typeInfo().castType(GraphQLObjectType.class);
        GraphQLExecutionNode root = new GraphQLExecutionNode(type, parameters.fields(), singletonList(data));
        return execute(executionContext, root);
    }

    private ExecutionResult execute(ExecutionContext executionContext, GraphQLExecutionNode root) {

        Queue<GraphQLExecutionNode> nodes = new ArrayDeque<>();
        nodes.add(root);

        while (!nodes.isEmpty()) {

            GraphQLExecutionNode node = nodes.poll();

            for (String fieldName : node.getFields().keySet()) {
                List<Field> fieldList = node.getFields().get(fieldName);
                List<GraphQLExecutionNode> childNodes = resolveField(executionContext, node.getParentType(),
                        node.getData(), fieldName, fieldList);
                nodes.addAll(childNodes);
            }
        }
        return new ExecutionResultImpl(getOnlyElement(root.getData()).getParentResult(), executionContext.getErrors());

    }

    private GraphQLExecutionNodeDatum getOnlyElement(List<GraphQLExecutionNodeDatum> list) {
        return list.get(0);
    }

    // Use the data.source objects to fetch
    // Use the data.parentResult objects to put values into.  These are either primitives or empty maps
    // If they were empty maps, we need that list of nodes to process

    private List<GraphQLExecutionNode> resolveField(ExecutionContext executionContext, GraphQLObjectType parentType,
                                                    List<GraphQLExecutionNodeDatum> nodeData, String fieldName, List<Field> fields) {

        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext.getGraphQLSchema(), parentType, fields.get(0));
        if (fieldDef == null) {
            return Collections.emptyList();
        }
        List<GraphQLExecutionNodeValue> values = fetchData(executionContext, parentType, nodeData, fields, fieldDef);

        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(
                fieldDef.getArguments(), fields.get(0).getArguments(), executionContext.getVariables());
        return completeValues(executionContext, parentType, values, fieldName, fields, fieldDef.getType(), argumentValues);
    }

    /**
     * Updates parents and returns new Nodes.
     */
    private List<GraphQLExecutionNode> completeValues(ExecutionContext executionContext, GraphQLObjectType parentType,
                                                      List<GraphQLExecutionNodeValue> values, String fieldName, List<Field> fields,
                                                      GraphQLOutputType outputType, Map<String, Object> argumentValues) {

        GraphQLType fieldType = handleNonNullType(outputType, values, parentType, fields);

        if (isPrimitive(fieldType)) {
            handlePrimitives(values, fieldName, fieldType);
            return Collections.emptyList();
        } else if (isObject(fieldType)) {
            return handleObject(executionContext, argumentValues, values, fieldName, fields, fieldType);
        } else if (isList(fieldType)) {
            return handleList(executionContext, argumentValues, values, fieldName, fields, parentType, (GraphQLList) fieldType);
        } else {
            throw new IllegalArgumentException("Unrecognized type: " + fieldType);
        }
    }

    @SuppressWarnings("unchecked")
    private List<GraphQLExecutionNode> handleList(ExecutionContext executionContext, Map<String, Object> argumentValues,
                                                  List<GraphQLExecutionNodeValue> values, String fieldName, List<Field> fields,
                                                  GraphQLObjectType parentType, GraphQLList listType) {

        List<GraphQLExecutionNodeValue> flattenedNodeValues = new ArrayList<>();

        for (GraphQLExecutionNodeValue value : values) {
            if (value.getValue() == null) {
                value.getResultContainer().putResult(fieldName, null);
            } else {
                GraphQLExecutionResultList flattenedDatum = value.getResultContainer().createAndPutEmptyChildList(
                        fieldName);
                for (Object rawValue : (List<Object>) value.getValue()) {
                    flattenedNodeValues.add(new GraphQLExecutionNodeValue(flattenedDatum, rawValue));
                }
            }
        }

        GraphQLOutputType subType = (GraphQLOutputType) listType.getWrappedType();
        return completeValues(executionContext, parentType, flattenedNodeValues, fieldName, fields, subType, argumentValues);

    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    private List<GraphQLExecutionNode> handleObject(ExecutionContext executionContext, Map<String, Object> argumentValues,
                                                    List<GraphQLExecutionNodeValue> values, String fieldName, List<Field> fields, GraphQLType fieldType) {

        ChildDataCollector collector = createAndPopulateChildData(executionContext, fields.get(0), values, fieldName, fieldType, argumentValues);

        List<GraphQLExecutionNode> childNodes =
                createChildNodes(executionContext, fields, collector);

        return childNodes;
    }

    private List<GraphQLExecutionNode> createChildNodes(ExecutionContext executionContext, List<Field> fields,
                                                        ChildDataCollector collector) {

        List<GraphQLExecutionNode> childNodes = new ArrayList<>();

        for (ChildDataCollector.Entry entry : collector.getEntries()) {
            Map<String, List<Field>> childFields = getChildFields(executionContext, entry.getObjectType(), fields);
            childNodes.add(new GraphQLExecutionNode(entry.getObjectType(), childFields, entry.getData()));
        }
        return childNodes;
    }

    private ChildDataCollector createAndPopulateChildData(ExecutionContext executionContext, Field field, List<GraphQLExecutionNodeValue> values, String fieldName,
                                                          GraphQLType fieldType, Map<String, Object> argumentValues) {
        ChildDataCollector collector = new ChildDataCollector();
        for (GraphQLExecutionNodeValue value : values) {
            if (value.getValue() == null) {
                // We hit a null, insert the null and do not create a child
                value.getResultContainer().putResult(fieldName, null);
            } else {
                GraphQLExecutionNodeDatum childDatum = value.getResultContainer().createAndPutChildDatum(fieldName, value.getValue());
                GraphQLObjectType graphQLObjectType = getGraphQLObjectType(executionContext, field, fieldType, value.getValue(), argumentValues);
                collector.putChildData(graphQLObjectType, childDatum);
            }
        }
        return collector;
    }

    private GraphQLType handleNonNullType(GraphQLType fieldType, List<GraphQLExecutionNodeValue> values,
            /*Nullable*/ GraphQLObjectType parentType, /*Nullable*/ List<Field> fields) {
        if (isNonNull(fieldType)) {
            for (GraphQLExecutionNodeValue value : values) {
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

        FieldCollectorParameters collectorParameters = newParameters(executionContext.getGraphQLSchema(), resolvedType)
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

    private void handlePrimitives(List<GraphQLExecutionNodeValue> values, String fieldName,
                                  GraphQLType type) {
        for (GraphQLExecutionNodeValue value : values) {
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
    private List<GraphQLExecutionNodeValue> fetchData(ExecutionContext executionContext, GraphQLObjectType parentType,
                                                      List<GraphQLExecutionNodeDatum> nodeData, List<Field> fields, GraphQLFieldDefinition fieldDef) {

        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(
                fieldDef.getArguments(), fields.get(0).getArguments(), executionContext.getVariables());
        List<Object> sources = new ArrayList<>();
        for (GraphQLExecutionNodeDatum n : nodeData) {
            sources.add(n.getSource());
        }

        GraphQLOutputType fieldType = fieldDef.getType();
        DataFetchingFieldSelectionSet fieldCollector = DataFetchingFieldSelectionSetImpl.newCollector(executionContext, fieldType, fields);

        DataFetchingEnvironment environment = new DataFetchingEnvironmentImpl(
                sources,
                argumentValues,
                executionContext.getContext(),
                executionContext.getRoot(),
                fields,
                fieldDef.getType(),
                parentType,
                executionContext.getGraphQLSchema(),
                executionContext.getFragmentsByName(),
                executionContext.getExecutionId(),
                fieldCollector);

        List<Object> values;
        try {
            values = (List<Object>) getDataFetcher(fieldDef).get(environment);
        } catch (Exception e) {
            values = new ArrayList<>(nodeData.size());
            log.warn("Exception while fetching data", e);
            handleDataFetchingException(executionContext, fieldDef, argumentValues, e);
        }

        List<GraphQLExecutionNodeValue> retVal = new ArrayList<>();
        for (int i = 0; i < nodeData.size(); i++) {
            retVal.add(new GraphQLExecutionNodeValue(nodeData.get(i), values.get(i)));
        }
        return retVal;
    }

    private BatchedDataFetcher getDataFetcher(GraphQLFieldDefinition fieldDef) {
        DataFetcher supplied = fieldDef.getDataFetcher();
        return batchingFactory.create(supplied);
    }
}
