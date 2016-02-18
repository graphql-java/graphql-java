package graphql.execution.batched;

import graphql.ExceptionWhileDataFetching;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLException;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategy;
import graphql.language.Field;
import graphql.schema.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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
    public ExecutionResult execute(ExecutionContext executionContext, GraphQLObjectType parentType, Object source, Map<String, List<Field>> fields) {
        GraphQLExecutionNodeDatum data = new GraphQLExecutionNodeDatum(new LinkedHashMap<String, Object>(), source);
        GraphQLExecutionNode root = new GraphQLExecutionNode(parentType, fields, singletonList(data));
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

        return completeValues(executionContext, parentType, values, fieldName, fields, fieldDef.getType());
    }

    /**
     * Updates parents and returns new Nodes.
     */
    private List<GraphQLExecutionNode> completeValues(ExecutionContext executionContext, GraphQLObjectType parentType,
                                                      List<GraphQLExecutionNodeValue> values, String fieldName, List<Field> fields,
                                                      GraphQLOutputType outputType) {

        GraphQLType fieldType = handleNonNullType(outputType, values, parentType, fields);

        if (isPrimitive(fieldType)) {
            handlePrimitives(values, fieldName, fieldType);
            return Collections.emptyList();
        } else if (isObject(fieldType)) {
            return handleObject(executionContext, values, fieldName, fields, fieldType);
        } else if (isList(fieldType)) {
            return handleList(executionContext, values, fieldName, fields, parentType, (GraphQLList) fieldType);
        } else {
            throw new IllegalArgumentException("Unrecognized type: " + fieldType);
        }
    }

    @SuppressWarnings("unchecked")
    private List<GraphQLExecutionNode> handleList(ExecutionContext executionContext,
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
        return completeValues(executionContext, parentType, flattenedNodeValues, fieldName, fields, subType);

    }

    private List<GraphQLExecutionNode> handleObject(ExecutionContext executionContext,
                                                    List<GraphQLExecutionNodeValue> values, String fieldName, List<Field> fields, GraphQLType fieldType) {

        ChildDataCollector collector = createAndPopulateChildData(values, fieldName, fieldType);

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

    private ChildDataCollector createAndPopulateChildData(List<GraphQLExecutionNodeValue> values, String fieldName,
                                                          GraphQLType fieldType) {
        ChildDataCollector collector = new ChildDataCollector();
        for (GraphQLExecutionNodeValue value : values) {
            if (value.getValue() == null) {
                // We hit a null, insert the null and do not create a child
                value.getResultContainer().putResult(fieldName, null);
            } else {
                GraphQLExecutionNodeDatum childDatum = value.getResultContainer().createAndPutChildDatum(fieldName, value.getValue());
                GraphQLObjectType graphQLObjectType = getGraphQLObjectType(fieldType, value.getValue());
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

        Map<String, List<Field>> subFields = new LinkedHashMap<>();
        List<String> visitedFragments = new ArrayList<>();
        for (Field field : fields) {
            if (field.getSelectionSet() == null) continue;
            fieldCollector.collectFields(executionContext, resolvedType, field.getSelectionSet(), visitedFragments, subFields);
        }
        return subFields;
    }

    private GraphQLObjectType getGraphQLObjectType(GraphQLType fieldType, Object value) {
        GraphQLObjectType resolvedType = null;
        if (fieldType instanceof GraphQLInterfaceType) {
            resolvedType = resolveType((GraphQLInterfaceType) fieldType, value);
        } else if (fieldType instanceof GraphQLUnionType) {
            resolvedType = resolveType((GraphQLUnionType) fieldType, value);
        } else if (fieldType instanceof GraphQLObjectType) {
            resolvedType = (GraphQLObjectType) fieldType;
        }
        return resolvedType;
    }

    private void handlePrimitives(List<GraphQLExecutionNodeValue> values, String fieldName,
                                  GraphQLType type) {
        for (GraphQLExecutionNodeValue value : values) {
            Object coercedValue = coerce(type, value.getValue());
            value.getResultContainer().putResult(fieldName, coercedValue);
        }
    }

    private Object coerce(GraphQLType type, Object value) {
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
        DataFetchingEnvironment environment = new DataFetchingEnvironment(
                sources,
                argumentValues,
                executionContext.getRoot(),
                fields,
                fieldDef.getType(),
                parentType,
                executionContext.getGraphQLSchema()
        );

        List<Object> values;
        try {
            values = (List<Object>) getDataFetcher(fieldDef).get(environment);
        } catch (Exception e) {
            values = new ArrayList<>();
            for (int i = 0; i < nodeData.size(); i++) {
                values.add(null);
            }
            log.info("Exception while fetching data", e);
            executionContext.addError(new ExceptionWhileDataFetching(e));
        }
        assert nodeData.size() == values.size();

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
