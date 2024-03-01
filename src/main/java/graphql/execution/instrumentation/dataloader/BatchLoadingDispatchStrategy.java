package graphql.execution.instrumentation.dataloader;

import graphql.Internal;
import graphql.TrivialDataFetcher;
import graphql.execution.DataLoaderDispatchStrategy;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.ResultPath;
import graphql.normalized.ExecutableNormalizedField;
import graphql.normalized.ExecutableNormalizedOperation;
import graphql.schema.BatchLoaderDataFetcher;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.util.FpKit;
import graphql.util.LockKit;
import org.dataloader.DataLoaderRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import static graphql.Assert.assertTrue;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;
import static graphql.schema.GraphQLTypeUtil.unwrapNonNull;

@Internal
public class BatchLoadingDispatchStrategy implements DataLoaderDispatchStrategy {

    private final ExecutionContext executionContext;

    private final LockKit.ReentrantLock stateLock = new LockKit.ReentrantLock();


//    private final Map<Integer, Set<ResultPath>> levelToExpectedFetchField = new ConcurrentHashMap<>();
//    private final Map<Integer, Set<ResultPath>> levelToHappenedFetchedFields = new ConcurrentHashMap<>();
//    private final Map<Integer, Set<ResultPath>> levelToHappenedFetchedFieldDone = new ConcurrentHashMap<>();
//    private final Map<Integer, Set<ResultPath>> levelToExpectedExecuteObject = new ConcurrentHashMap<>();
//    private final Map<Integer, Set<ResultPath>> levelToHappenedExecuteObject = new ConcurrentHashMap<>();
//    private final Map<Integer, Set<ResultPath>> levelToTrivialDataFetchersFetched = new ConcurrentHashMap<>();
//    private final Map<Integer, Set<ChainedDataLoader<?, ?>>> levelToChainedDataLoaders = new ConcurrentHashMap<>();
//    private final Map<ResultPath, CompletableFuture<?>> pathToSecondDataLoaderCalled = new ConcurrentHashMap<>();
//    private final Set<ResultPath> trivialDFs = ConcurrentHashMap.newKeySet();
//

    private final Map<ResultPath, ExecutionNode> pathToExecutionNode = new ConcurrentHashMap<>();
    private final Map<String, DispatchPoint> dataLoaderToDispatchPoint = new ConcurrentHashMap<>();

    public BatchLoadingDispatchStrategy(ExecutionContext executionContext) {
        this.executionContext = executionContext;
    }

    public enum ExecutionNodeState {
        EXPECT_FETCH_FIELD,
        HAPPENED_FETCH_FIELD,
        FETCH_FIELD_DONE,
        EXPECT_EXECUTE_OBJECT,
        HAPPENED_EXECUTE_OBJECT,
    }

    static class ExecutionNode {
        final ResultPath path;
        final ExecutionNode parent;
        final List<ExecutionNode> children = new CopyOnWriteArrayList<>();

        public volatile ExecutionNodeState state;

        public ExecutionNode(ResultPath path, ExecutionNode parent) {
            this.path = path;
            this.parent = parent;
            this.state = ExecutionNodeState.EXPECT_EXECUTE_OBJECT;
        }

        @Override
        public String toString() {
            return "ExecutionNode{" +
                    "path=" + path +
                    ", state=" + state +
                    ", children=" + children +
                    '}';
        }
    }

    private void makeLevelReady(int level) {
        stateLock.runLocked(() -> {
//            if (levelToExpectedFetchField.containsKey(level)) {
//                return;
//            }
//            levelToExpectedFetchField.put(level, ConcurrentHashMap.newKeySet());
//            levelToHappenedFetchedFields.put(level, ConcurrentHashMap.newKeySet());
//            levelToExpectedExecuteObject.put(level, ConcurrentHashMap.newKeySet());
//            levelToHappenedExecuteObject.put(level, ConcurrentHashMap.newKeySet());
//            levelToHappenedFetchedFieldDone.put(level, ConcurrentHashMap.newKeySet());
//            levelToChainedDataLoaders.put(level, ConcurrentHashMap.newKeySet());
        });
    }


    @Override
    public void executionStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        Supplier<ExecutableNormalizedOperation> normalizedQueryTree = executionContext.getNormalizedQueryTree();
        ExecutableNormalizedOperation executableNormalizedOperation = normalizedQueryTree.get();
        GraphQLSchema graphQLSchema = executionContext.getGraphQLSchema();
        GraphQLCodeRegistry codeRegistry = executionContext.getGraphQLSchema().getCodeRegistry();

        Map<DFKey, DF> pathToDF = new LinkedHashMap<>();
        List<DF> rootDFs = buildDataFetcherTree(executableNormalizedOperation, graphQLSchema, codeRegistry, pathToDF);

        dataLoaderToDispatchPoint.putAll(createDispatchPoints(rootDFs));

//        Map<String, List<DF>> dataLoaderToDF = new LinkedHashMap<>();
//        for (DFKey dfKey : pathToDF.keySet()) {
//            DF df = pathToDF.get(dfKey);
//            if (df.batchLoadersUsed.isEmpty()) {
//                continue;
//            }
//            for (String dataLoaderName : df.batchLoadersUsed) {
//                if (!dataLoaderToDF.containsKey(dataLoaderName)) {
//                    dataLoaderToDF.put(dataLoaderName, new ArrayList<>());
//                }
//                dataLoaderToDF.get(dataLoaderName).add(df);
//            }
//        }
//        // we wait for the same DFs using the same dataloader and only dispatch then
//        for (String dataLoader : dataLoaderToDF.keySet()) {
//            DispatchPointNode dispatchPointNode = new DispatchPointNode();
//            dispatchPointNode.dataLoaderName = dataLoader;
//            for (DF df : dataLoaderToDF.get(dataLoader)) {
//                dispatchPointNode.dfKeys.add(df.dfKey);
//            }
//            dispatchPointNodes.add(dispatchPointNode);
//        }

        ResultPath rootPath = parameters.getPath();
        assertTrue(rootPath.getLevel() == 0);
        stateLock.runLocked(() -> {
            makeLevelReady(1);
            ExecutionNode rootExecutionNode = new ExecutionNode(rootPath, null);
            rootExecutionNode.state = ExecutionNodeState.HAPPENED_EXECUTE_OBJECT;
            pathToExecutionNode.put(rootPath, rootExecutionNode);
            parameters.getFields().getKeys().forEach(key -> {
                ExecutionNode executionNode = new ExecutionNode(rootPath.segment(key), rootExecutionNode);
                rootExecutionNode.children.add(executionNode);
                executionNode.state = ExecutionNodeState.EXPECT_FETCH_FIELD;
                pathToExecutionNode.put(rootPath.segment(key), executionNode);
                checkNewNodeRelevantForDispatchPoint(executionNode);
            });
        });
    }

    static class DispatchPoint {
        // we wait for all DFS matching the keys
        final List<DFKey> dfKeys = new ArrayList<>();
        volatile String dataLoaderName;
        // A DispatchPoint with the same dataLoaderName, but with dfKeys being children of this dfKeys,
        // meaning this dispatch point must be executed after this
        volatile DispatchPoint child;
        volatile boolean alreadyDispatched;

        final Map<DFKey, List<ExecutionNode>> relevantExecutionNodes = new ConcurrentHashMap<>();

        @Override
        public String toString() {
            return "DispatchPoint{" +
                    "dfKeys=" + dfKeys +
                    ", dataLoaderName='" + dataLoaderName + '\'' +
                    '}';
        }
    }


    static class DF {
        boolean trivialDF;
        String objectName;
        String fieldName;
        String key;
        List<String> batchLoadersUsed = new ArrayList<>();
        List<DF> children = new ArrayList<>();
        DFKey dfKey;
    }

    private Map<String, DispatchPoint> createDispatchPoints(List<DF> rootDFs) {
        Map<String, DispatchPoint> result = new LinkedHashMap<>();
        for (DF rootDF : rootDFs) {
            if (!rootDF.batchLoadersUsed.isEmpty()) {
                DispatchPoint dispatchPoint;
                if (!result.containsKey(rootDF.batchLoadersUsed.get(0))) {
                    dispatchPoint = new DispatchPoint();
                    result.put(rootDF.batchLoadersUsed.get(0), dispatchPoint);
                    dispatchPoint.dataLoaderName = rootDF.batchLoadersUsed.get(0);
                    result.put(dispatchPoint.dataLoaderName, dispatchPoint);
                } else {
                    dispatchPoint = result.get(rootDF.batchLoadersUsed.get(0));
                }
                dispatchPoint.dfKeys.add(rootDF.dfKey);
            }
            createDispatchPointsImpl(rootDF, result);
        }
        return result;
    }

    private void createDispatchPointsImpl(DF df, Map<String, DispatchPoint> result) {
        for (DF child : df.children) {
            if (!child.batchLoadersUsed.isEmpty()) {
                DispatchPoint dispatchPoint;
                DFKey newDFKey = child.dfKey;
                if (!result.containsKey(child.batchLoadersUsed.get(0))) {
                    dispatchPoint = new DispatchPoint();
                    result.put(child.batchLoadersUsed.get(0), dispatchPoint);
                    dispatchPoint.dataLoaderName = child.batchLoadersUsed.get(0);
                    result.put(dispatchPoint.dataLoaderName, dispatchPoint);
                    dispatchPoint.dfKeys.add(newDFKey);
                } else {
                    dispatchPoint = result.get(child.batchLoadersUsed.get(0));
                    // we need to check if the child.dfKey is a path that comes after one of the
                    // current dispatchPoint dfKeys. If so it is a
                    outer:
                    while (true) {
                        for (DFKey dfKey : dispatchPoint.dfKeys) {
                            if (!isChildDFKey(dfKey, newDFKey)) {
                                continue;
                            }
                            // the newDFKey is a child of one of the current DFKeys, meaning
                            // we can't dispatch all of them together. Therefore we try the next
                            // child or crate a new DispatchPoint
                            if (dispatchPoint.child != null) {
                                dispatchPoint = dispatchPoint.child;
                                continue outer;
                            } else {
                                DispatchPoint newDispatchPoint = new DispatchPoint();
                                newDispatchPoint.dataLoaderName = child.batchLoadersUsed.get(0);
                                dispatchPoint.child = newDispatchPoint;
                                dispatchPoint = newDispatchPoint;
                                break outer;
                            }
                        }
                        // the newDFKey is not a child of any of the current DFKeys
                        break;
                    }
                    // we can add the new DFKey to the existing DispatchPoint
                    dispatchPoint.dfKeys.add(newDFKey);

                }
            }
            createDispatchPointsImpl(child, result);
        }

    }

    private boolean isChildDFKey(DFKey parent, DFKey possibleChild) {
        // the possibleChild is a child if the parent is a "prefix" of the possibleChild
        return startWith(possibleChild.getKeys(), parent.getKeys());
    }

    private boolean isDFKeyChildOrEqualToResultPath(ResultPath parent, DFKey possibleChild) {
        // the possibleChild is a child if the parent is a "prefix" of the possibleChild
        return startWith(possibleChild.getKeys(), parent.getKeysOnly());
    }

    private boolean isDFKeyParentOrEqualToResultPath(ResultPath parent, DFKey possibleParent) {
        // the possibleChild is a child if the parent is a "prefix" of the possibleChild
        return startWith(parent.getKeysOnly(), possibleParent.getKeys());
    }

    private List<DF> buildDataFetcherTree(ExecutableNormalizedOperation executableNormalizedOperation,
                                          GraphQLSchema graphQLSchema,
                                          GraphQLCodeRegistry codeRegistry,
                                          Map<DFKey, DF> pathToDF) {
        List<ExecutableNormalizedField> rootFields = executableNormalizedOperation.getTopLevelFields();
        List<DF> rootDFs = new ArrayList<>();
        for (ExecutableNormalizedField rootField : rootFields) {
            Set<String> objectTypeNames = rootField.getObjectTypeNames();
            for (String objectTypeName : objectTypeNames) {
                DF rootDF = createENF(rootField, objectTypeName, graphQLSchema, codeRegistry);
                rootDF.dfKey = new DFKey(List.of(rootDF.key));
                pathToDF.put(rootDF.dfKey, rootDF);
                buildDFTreeImpl(rootField, rootDF, graphQLSchema, codeRegistry, pathToDF);
                rootDFs.add(rootDF);
            }
        }
        return rootDFs;
    }

    private DF createENF(ExecutableNormalizedField field,
                         String objectTypeName,
                         GraphQLSchema graphQLSchema,
                         GraphQLCodeRegistry codeRegistry
    ) {
        DF result = new DF();
        GraphQLObjectType objectType = graphQLSchema.getObjectType(objectTypeName);
        GraphQLFieldDefinition fieldDefinition = objectType.getFieldDefinition(field.getFieldName());
        DataFetcher<?> dataFetcher = codeRegistry.getDataFetcher(FieldCoordinates.coordinates(objectTypeName, field.getName()), fieldDefinition);
        if (dataFetcher instanceof BatchLoaderDataFetcher) {
            result.batchLoadersUsed.addAll(((BatchLoaderDataFetcher<?>) dataFetcher).getDataLoaderNames());
        } else if (dataFetcher instanceof TrivialDataFetcher) {
            result.trivialDF = true;
        }
        result.fieldName = field.getFieldName();
        result.objectName = objectTypeName;
        result.key = field.getResultKey();
        return result;
    }

    private void buildDFTreeImpl(ExecutableNormalizedField field,
                                 DF df,
                                 GraphQLSchema graphQLSchema,
                                 GraphQLCodeRegistry codeRegistry,
                                 Map<DFKey, DF> pathToDF) {
        List<ExecutableNormalizedField> children = field.getChildren();
        for (ExecutableNormalizedField child : children) {
            Set<String> objectTypeNames = child.getObjectTypeNames();
            for (String objectTypeName : objectTypeNames) {
                DF childDF = createENF(child, objectTypeName, graphQLSchema, codeRegistry);
                childDF.dfKey = df.dfKey.withAdditionalKey(childDF.key);
                pathToDF.put(childDF.dfKey, childDF);
                buildDFTreeImpl(child, childDF, graphQLSchema, codeRegistry, pathToDF);
                df.children.add(childDF);
            }
        }
    }

    @Override
    public void executeObject(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        ResultPath path = parameters.getPath();
        int level = getLevelForPath(path) + 1;
//        System.out.println("EXECUTE OBJECT at " + path);
        stateLock.runLocked(() -> {
            makeLevelReady(level);
            // the path is the root field of the execute object
            // and the leve is one more than the path length
            ExecutionNode currentExecutionNode = pathToExecutionNode.get(path);
            currentExecutionNode.state = ExecutionNodeState.HAPPENED_EXECUTE_OBJECT;
            parameters.getFields().getKeys().forEach(key -> {
                ExecutionNode executionNode = new ExecutionNode(path.segment(key), currentExecutionNode);
                currentExecutionNode.children.add(executionNode);
                executionNode.state = ExecutionNodeState.EXPECT_FETCH_FIELD;
                pathToExecutionNode.put(path.segment(key), executionNode);
                checkNewNodeRelevantForDispatchPoint(executionNode);
            });
        });
    }

    private int getLevelForPath(ResultPath resultPath) {
//        int trivialCount = 0;
//        String resultPathString = resultPath.toString();
//        for (ResultPath trivialPath : trivialDFs) {
//            String str = trivialPath.toString();
//            if (resultPathString.startsWith(str)) {
//                trivialCount++;
//            }
//        }
//        return resultPath.getLevel() - trivialCount;
        return resultPath.getLevel();
    }


    @Override
    public void fieldFetched(ExecutionContext executionContext, ExecutionStrategyParameters parameters, DataFetcher<?> dataFetcher, Object fetchedValue) {
        ResultPath path = parameters.getPath();
        int level = getLevelForPath(path);
//        System.out.println("field fetched at " + path);
        DispatchPoint dispatchNeeded = stateLock.callLocked(() -> {
            pathToExecutionNode.get(path).state = ExecutionNodeState.HAPPENED_FETCH_FIELD;
            return isDispatchReady();
        });
        if (dispatchNeeded != null) {
            dispatch(dispatchNeeded);
        }
    }

    @Override
    public void fieldFetchedDone(ExecutionContext executionContext, ExecutionStrategyParameters parameters, DataFetcher<?> dataFetcher, Object value, GraphQLObjectType parentType, GraphQLFieldDefinition fieldDefinition) {
        ResultPath path = parameters.getPath();
        int level = getLevelForPath(parameters.getPath());
        GraphQLType fieldType = fieldDefinition.getType();
        GraphQLType unwrappedFieldType = unwrapAll(fieldType);
        DispatchPoint dispatchReady = null;
        makeLevelReady(level + 1);


        if (value != null && (GraphQLTypeUtil.isObjectType(unwrappedFieldType) || GraphQLTypeUtil.isInterfaceOrUnion(unwrappedFieldType))) {
            // now we know we have a composite type wrapped in n lists (n can be 0)
            makeLevelReady(level + 1);
            Set<ResultPath> executeObjectPaths = new LinkedHashSet<>();
            if (isList(unwrapNonNull(fieldType))) {
                handeList(value, fieldType, path, executeObjectPaths);
                dispatchReady = stateLock.callLocked(() -> {
                    ExecutionNode currentExecutionNode = pathToExecutionNode.get(path);
                    currentExecutionNode.state = ExecutionNodeState.FETCH_FIELD_DONE;
                    // executeObjectPath can be empty if the list contains only null values
                    for (ResultPath executeObjectPath : executeObjectPaths) {
                        ExecutionNode childExecutionNode = new ExecutionNode(executeObjectPath, currentExecutionNode);
                        currentExecutionNode.children.add(childExecutionNode);
                        pathToExecutionNode.put(executeObjectPath, childExecutionNode);
                        checkNewNodeRelevantForDispatchPoint(childExecutionNode);
                    }
                    return isDispatchReady();
                });
            } else {
                // just changing the current node to EXPECT_EXECUTE_OBJECT
                executeObjectPaths.add(path);
                stateLock.runLocked(() -> {
                    pathToExecutionNode.get(path).state = ExecutionNodeState.EXPECT_EXECUTE_OBJECT;
                });
            }
        } else {
            // just changing the current one to FETCH_FIELD_DONE, because there are no children
            dispatchReady = stateLock.callLocked(() -> {
                pathToExecutionNode.get(path).state = ExecutionNodeState.FETCH_FIELD_DONE;
                return isDispatchReady();
            });
        }
        if (dispatchReady != null) {
            dispatch(dispatchReady);
        }

    }

    private void handeList(Object currentList, GraphQLType currentType, ResultPath currentPath, Set<ResultPath> result) {
        if (!isList(unwrapNonNull(currentType))) {
            return;
        }
        if (FpKit.isIterable(currentList)) {
            Iterable<Object> iterable = FpKit.toIterable(currentList);
            int index = 0;
            for (Object item : iterable) {
                if (item != null) {
                    result.add(currentPath.segment(index));
                    handeList(item, ((GraphQLList) currentType).getWrappedType(), currentPath.segment(index), result);
                }
                index++;
            }
        }
    }

    private void checkNewNodeRelevantForDispatchPoint(ExecutionNode newNode) {
        for (String dataLoader : dataLoaderToDispatchPoint.keySet()) {
            DispatchPoint dispatchPoint = dataLoaderToDispatchPoint.get(dataLoader);
            while (dispatchPoint != null) {
                Map<DFKey, List<ExecutionNode>> currentRelevantNodes = dispatchPoint.relevantExecutionNodes;
                if (newNode.path.getLevel() == 1) {
                    for (DFKey dfKey : dispatchPoint.dfKeys) {
                        if (isDFKeyChildOrEqualToResultPath(newNode.path, dfKey)) {
                            if (!currentRelevantNodes.containsKey(dfKey)) {
                                currentRelevantNodes.put(dfKey, new ArrayList<>());
                            }
                            currentRelevantNodes.get(dfKey).add(newNode);
                        }
                    }
                } else {
                    // non root execution nodes
                    for (DFKey dfKey : dispatchPoint.dfKeys) {
                        if (isDFKeyChildOrEqualToResultPath(newNode.path, dfKey)) {
                            // this means the parent node was relevant and we remove it
                            if (!currentRelevantNodes.containsKey(dfKey)) {
                                currentRelevantNodes.put(dfKey, new ArrayList<>());
                            }
                            currentRelevantNodes.get(dfKey).remove(newNode.parent);
                            currentRelevantNodes.get(dfKey).add(newNode);
                        } else if (isDFKeyParentOrEqualToResultPath(newNode.path, dfKey)) {
//                            System.out.println("already progressed execution node" + newNode.path + " vs " + dfKey);
                        }
                    }
                }
                dispatchPoint = dispatchPoint.child;
            }
        }

    }

    private DispatchPoint isDispatchReady() {
        outer:
        for (String dataLoaderName : dataLoaderToDispatchPoint.keySet()) {
            DispatchPoint dispatchPoint = dataLoaderToDispatchPoint.get(dataLoaderName);
            while (dispatchPoint != null && dispatchPoint.alreadyDispatched) {
                dispatchPoint = dispatchPoint.child;
            }
            if (dispatchPoint == null) {
                continue;
            }
            for (DFKey dfKey : dispatchPoint.relevantExecutionNodes.keySet()) {
                List<ExecutionNode> executionNodes = dispatchPoint.relevantExecutionNodes.get(dfKey);
                // all relevant nodes must be either done fetching (if it is a parent of the dfkey)
                // or the fetched must have happened (meaning the dataloader was invoked, but it is pending)
                // or the DataLoader already resolved (because of caching) and the execution progressed,
                for (ExecutionNode executionNode : executionNodes) {
                    if (executionNode.path.getKeysOnly().equals(dfKey.getKeys())) {
                        if (executionNode.state != ExecutionNodeState.HAPPENED_FETCH_FIELD && executionNode.state != ExecutionNodeState.HAPPENED_EXECUTE_OBJECT) {
                            continue outer;
                        }
                    } else {
                        if (executionNode.state != ExecutionNodeState.FETCH_FIELD_DONE) {
                            continue outer;
                        }
                    }
                }
            }
            return dispatchPoint;
        }
        return null;
    }

    private boolean isDFKeyReady(DFKey dfKey) {
        ArrayList<ExecutionNode> closesMatchingNodes = new ArrayList<>();
        findClosesMatchingNodes(pathToExecutionNode.get(ResultPath.rootPath()), dfKey, closesMatchingNodes);
        System.out.println("closesMatchingNodes: " + closesMatchingNodes);
        return false;
    }

    private void findClosesMatchingNodes(ExecutionNode executionNode, DFKey dfKey, List<ExecutionNode> result) {
        for (ExecutionNode child : executionNode.children) {
            ResultPath path = child.path;
            if (startWith(dfKey.getKeys(), path.getKeysOnly())) {
                result.add(child);
                findClosesMatchingNodes(child, dfKey, result);
            }
        }
    }

    // if the first starts with the second, meaning the size of the first must be greater or equal to the second
    private boolean startWith(List<String> longerList, List<String> possibleStart) {
        int ix = 0;
        if (possibleStart.size() > longerList.size()) {
            return false;
        }
        for (int i = 0; i < possibleStart.size(); i++) {
            if (!longerList.get(i).equals(possibleStart.get(i))) {
                return false;
            }
        }
        return true;
    }

    private List<ExecutionNode> findMatchingNodes(DFKey dfKey) {
        List<ExecutionNode> result = new ArrayList<>();
        for (ResultPath resultPath : pathToExecutionNode.keySet()) {
            if (doesPathMatchDFKey(resultPath, dfKey)) {
                result.add(pathToExecutionNode.get(resultPath));
            }
        }
        return result;
    }

    private boolean doesPathMatchDFKey(ResultPath path, DFKey dfKey) {
        return path.getKeysOnly().equals(dfKey.getKeys());
    }


    void dispatch(DispatchPoint dispatchPoint) {
        System.out.println("DISPATCH point!!" + dispatchPoint);
        dispatchPoint.alreadyDispatched = true;
        DataLoaderRegistry dataLoaderRegistry = executionContext.getDataLoaderRegistry();
        dataLoaderRegistry.getDataLoader(dispatchPoint.dataLoaderName).dispatch();
        // once all first DataLoaders are finished an
//        if (levelToChainedDataLoaders.containsKey(level)) {
//            Set<ChainedDataLoader<?, ?>> chainedDataLoaders = levelToChainedDataLoaders.get(level);
//            Async.CombinedBuilder<Void> futures = Async.ofExpectedSize(chainedDataLoaders.size());
//            for (ChainedDataLoader<?, ?> chainedDataLoader : chainedDataLoaders) {
//                futures.add(chainedDataLoader.getSecondDataLoaderCalled());
//            }
//            futures.await().thenAccept((voids) -> {
//                System.out.println("CALLING DISPATCH AGAIN!");
//                dataLoaderRegistry.dispatchAll();
//            });
//        }
    }

    public static void main(String[] args) {
        outer:
        while (true) {
            System.out.println("yyoo");
            break outer;
        }

    }


}
