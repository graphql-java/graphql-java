package graphql.execution.instrumentation.dataloader;

import graphql.Internal;
import graphql.execution.DataLoaderDispatchStrategy;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.FieldValueInfo;
import graphql.execution.ResultPath;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.util.FpKit;
import graphql.util.LockKit;
import org.dataloader.DataLoaderRegistry;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static graphql.Assert.assertTrue;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;
import static graphql.schema.GraphQLTypeUtil.unwrapNonNull;

@Internal
public class BatchLoadingDispatchStrategy implements DataLoaderDispatchStrategy {

    private final ExecutionContext executionContext;

    private final LockKit.ReentrantLock stateLock = new LockKit.ReentrantLock();

    private final AtomicInteger dispatchCounter = new AtomicInteger(0);


    private final Map<Integer, Set<ResultPath>> levelToExpectedFetchField = new ConcurrentHashMap<>();
    private final Map<Integer, Set<ResultPath>> levelToHappenedFetchedFields = new ConcurrentHashMap<>();
    private final Map<Integer, Set<ResultPath>> levelToHappenedFetchedFieldDone = new ConcurrentHashMap<>();
    private final Map<Integer, Set<ResultPath>> levelToExpectedExecuteObject = new ConcurrentHashMap<>();
    private final Map<Integer, Set<ResultPath>> levelToHappenedExecuteObject = new ConcurrentHashMap<>();


    private final Set<ResultPath> expectedStrategyCalls = new LinkedHashSet<>();


    public BatchLoadingDispatchStrategy(ExecutionContext executionContext) {
        this.executionContext = executionContext;
    }

    private void makeLevelReady(int level) {
        stateLock.runLocked(() -> {
            if (levelToExpectedFetchField.containsKey(level)) {
                return;
            }
            levelToExpectedFetchField.put(level, ConcurrentHashMap.newKeySet());
            levelToHappenedFetchedFields.put(level, ConcurrentHashMap.newKeySet());
            levelToExpectedExecuteObject.put(level, ConcurrentHashMap.newKeySet());
            levelToHappenedExecuteObject.put(level, ConcurrentHashMap.newKeySet());
            levelToHappenedFetchedFieldDone.put(level, ConcurrentHashMap.newKeySet());
        });
    }


    @Override
    public void executionStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        System.out.println("execution strategy:  " + parameters.getPath());
        // entry for the strategy: we expect fetches for all root fields
        ResultPath rootPath = parameters.getPath();
        assertTrue(rootPath.getLevel() == 0);
        stateLock.runLocked(() -> {
            makeLevelReady(1);
            // the level one execute object is just the root path
            levelToExpectedExecuteObject.get(1).add(rootPath);
            levelToHappenedExecuteObject.get(1).add(rootPath);
            parameters.getFields().getKeys().forEach(key -> {
                levelToExpectedFetchField.get(1).add(rootPath.segment(key));
            });
        });
    }

    @Override
    public void executionStrategyOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {
//        System.out.println("execution strategy on field: " + parameters.getPath());
//        handleOnFieldValuesInfo(fieldValueInfoList, parameters);
    }

    @Override
    public void executeObject(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        ResultPath path = parameters.getPath();
        int level = path.getLevel() + 1;
        System.out.println("execute object path:  " + (parameters.getPath().isRootPath() ? "/" : parameters.getPath()) + " at level " + level);
        stateLock.runLocked(() -> {
            makeLevelReady(level);
            // the path is the root field of the execute object
            // and the leve is one more than the path length
            levelToHappenedExecuteObject.get(level).add(path);
            parameters.getFields().getKeys().forEach(key -> {
                levelToExpectedFetchField.get(level).add(path.segment(key));
            });
        });
    }

    @Override
    public void executeObjectOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {
//        System.out.println("execute object on field values info path:  " + parameters.getPath());
//        handleOnFieldValuesInfo(fieldValueInfoList, parameters);
    }

    @Override
    public void fieldFetched(ExecutionContext executionContext, ExecutionStrategyParameters parameters, DataFetcher<?> dataFetcher, CompletableFuture<Object> fetchedValue) {
        ResultPath path = parameters.getPath();
        int level = path.getLevel();
        System.out.println("field fetch: " + path + " at level " + level);
        boolean dispatchNeeded = stateLock.callLocked(() -> {
            levelToHappenedFetchedFields.get(level).add(path);
            return isDispatchReady(level);
        });
        if (dispatchNeeded) {
            dispatch(level);
        }
    }

    @Override
    public void fieldFetchedDone(ExecutionContext executionContext, ExecutionStrategyParameters parameters, DataFetcher<?> dataFetcher, Object value, GraphQLObjectType parentType, GraphQLFieldDefinition fieldDefinition) {
        ResultPath path = parameters.getPath();
        int level = parameters.getPath().getLevel();
        GraphQLType fieldType = fieldDefinition.getType();
        System.out.println("field fetched done on " + path);// + " with type " + fieldDefinition);
        GraphQLType unwrappedFieldType = unwrapAll(fieldType);
        boolean dispatchReady;
        makeLevelReady(level + 1);
        if (value != null && (GraphQLTypeUtil.isObjectType(unwrappedFieldType) || GraphQLTypeUtil.isInterfaceOrUnion(unwrappedFieldType))) {
            // now we know we have a composite type wrapped in n lists (n can be 0)
            makeLevelReady(level + 1);
            Set<ResultPath> executeObjectPaths = new LinkedHashSet<>();
            if (isList(unwrapNonNull(fieldType))) {
                handeList(value, fieldType, path, executeObjectPaths);
            } else {
                executeObjectPaths.add(path);
            }
            dispatchReady = stateLock.callLocked(() -> {
                levelToHappenedFetchedFieldDone.get(level).add(path);
                System.out.println("adding from list: " + executeObjectPaths + " at level " + (level + 1));
                levelToExpectedExecuteObject.get(level + 1).addAll(executeObjectPaths);
                return isDispatchReady(level + 1);
            });
        } else {
            dispatchReady = stateLock.callLocked(() -> {
                levelToHappenedFetchedFieldDone.get(level).add(path);
                return isDispatchReady(level + 1);
            });
        }
        if (dispatchReady) {
            dispatch(level + 1);
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

//    private void handleOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfos, ExecutionStrategyParameters parameters) {
//        int curLevel = parameters.getPath().getLevel() + 1;
//        boolean dispatch = stateLock.callLocked(() -> {
//            ResultPath path = parameters.getPath();
//            System.out.println("happened on field value: " + (path.isRootPath() ? "/" : path) + " at level " + curLevel);
//            levelToHappenedOnFieldValue.get(curLevel).add(path);
//            makeLevelReady(curLevel + 1);
//            Set<ResultPath> expectedExecuteObject = new LinkedHashSet<>();
//            objectPathsBasedOnFieldValues(parameters.getPath(), fieldValueInfos, parameters.getFields().getKeys(), expectedExecuteObject);
//            System.out.println("expected execute objects: at level " + (curLevel + 1) + " : " + expectedExecuteObject);
//            levelToExpectedExecuteObject.get(curLevel + 1).addAll(expectedExecuteObject);
//            // checking if we are ready to
//            return isDispatchReady(curLevel + 1);
//        });
//        if (dispatch) {
//            dispatch(curLevel + 1);
//        }
//    }

    private void objectPathsBasedOnFieldValues(ResultPath path, List<FieldValueInfo> fieldValueInfos, List<String> fieldKeys, Set<ResultPath> result) {
        int ix = 0;
        for (FieldValueInfo fieldValueInfo : fieldValueInfos) {
            if (fieldValueInfo.getCompleteValueType() == FieldValueInfo.CompleteValueType.OBJECT) {
                result.add(path.segment(fieldKeys.get(ix)));
            } else if (fieldValueInfo.getCompleteValueType() == FieldValueInfo.CompleteValueType.LIST) {
                handeList(path.segment(fieldKeys.get(ix)), fieldValueInfo.getFieldValueInfos(), result);
            }
            ix++;
        }
    }

    private void handeList(ResultPath path, List<FieldValueInfo> fieldValueInfos, Set<ResultPath> result) {
        int ix = 0;
        for (FieldValueInfo fieldValueInfo : fieldValueInfos) {
            if (fieldValueInfo.getCompleteValueType() == FieldValueInfo.CompleteValueType.OBJECT) {
                result.add(path.segment(ix));
            } else if (fieldValueInfo.getCompleteValueType() == FieldValueInfo.CompleteValueType.LIST) {
                handeList(path.segment(ix), fieldValueInfo.getFieldValueInfos(), result);
            }
            ix++;
        }
    }


    private boolean isDispatchReady(int level) {
        if (level == 1) {
            // level 1 is special: there is only one strategy call and that's it
            return allFetchesHappened(1);
        }
        if (isDispatchReady(level - 1) && levelToHappenedFetchedFieldDone.get(level - 1).equals(levelToHappenedFetchedFields.get(level - 1))
                && allExecuteObjectHappened(level) && allFetchesHappened(level)) {

            return true;
        }
//        System.out.println("NOT READY: ");
//        System.out.println("levelToExpectedExecuteObject" + levelToExpectedExecuteObject);
//        System.out.println("levelToHappenedExecuteObject" + levelToHappenedExecuteObject);
//        System.out.println("levelToExpectedFetchField" + levelToExpectedFetchField);
//        System.out.println("levelToHappenedFetchedFields" + levelToHappenedFetchedFields);
//        System.out.println("levelToHappenedFetchedFieldDone" + levelToHappenedFetchedFieldDone);
        return false;
    }

    private boolean allFetchesHappened(int level) {
        return levelToExpectedFetchField.get(level).equals(levelToHappenedFetchedFields.get(level));
    }

    private boolean allFetchedDoneHappened(int level) {
        return levelToHappenedFetchedFields.get(level).equals(levelToExpectedExecuteObject.get(level));
    }

    private boolean allExecuteObjectHappened(int level) {
        return levelToExpectedExecuteObject.get(level).equals(levelToHappenedExecuteObject.get(level));
    }

    void dispatch(int level) {
        System.out.println("DISPATCH!! level : " + level);
        DataLoaderRegistry dataLoaderRegistry = executionContext.getDataLoaderRegistry();
        dataLoaderRegistry.dispatchAll();
    }


}
