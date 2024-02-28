package graphql.execution.instrumentation.dataloader;

import graphql.Internal;
import graphql.TrivialDataFetcher;
import graphql.execution.Async;
import graphql.execution.DataLoaderDispatchStrategy;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;
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

    /**
     * The heart of the tracking:
     * Every field of type object or list of object (or list of list of object and so on) with non null
     * results leads to an execute object call. This expectation is tracked in levelToExpectedExecuteObject.
     * <p>
     * Every execute object leads to n fetch field calls. This expectation is tracked in levelToExpectedFetchField.
     * <p>
     * The fields which are actually finished fetching are tracked in levelToHappenedFetchedFieldDone.
     * <p>
     * Execute Strategy is fundamentally the same as execute object, but execute strategy happens
     * only once in the beginning of the execution and execute object happens every time an object is completed.
     * Example walk through:
     * 1. executeStrategy: execute Strategy leads to n expected fetch fields (the root fields of the operation)
     * 2. fetch field: each fetch field is documented as happened fetch field
     * 3. fetch field done: each "fetch field done" leads to expected "execute object" calls.
     * 4. execute object: Each execute object leads to expected fetch fields
     * <p>
     * After each fetched field and fetch field done, we check if the level is ready to dispatch.
     */

    private final Map<Integer, Set<ResultPath>> levelToExpectedFetchField = new ConcurrentHashMap<>();
    private final Map<Integer, Set<ResultPath>> levelToHappenedFetchedFields = new ConcurrentHashMap<>();
    private final Map<Integer, Set<ResultPath>> levelToHappenedFetchedFieldDone = new ConcurrentHashMap<>();
    private final Map<Integer, Set<ResultPath>> levelToExpectedExecuteObject = new ConcurrentHashMap<>();
    private final Map<Integer, Set<ResultPath>> levelToHappenedExecuteObject = new ConcurrentHashMap<>();
    private final Map<Integer, Set<ResultPath>> levelToTrivialDataFetchersFetched = new ConcurrentHashMap<>();
    private final Map<Integer, Set<ChainedDataLoader<?, ?>>> levelToChainedDataLoaders = new ConcurrentHashMap<>();
    private final Map<ResultPath, CompletableFuture<?>> pathToSecondDataLoaderCalled = new ConcurrentHashMap<>();
    private final Set<ResultPath> trivialDFs = ConcurrentHashMap.newKeySet();


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
            levelToChainedDataLoaders.put(level, ConcurrentHashMap.newKeySet());
        });
    }


    @Override
    public void executionStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
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
    public void executeObject(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        ResultPath path = parameters.getPath();
        int level = getLevelForPath(path) + 1;
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

    private int getLevelForPath(ResultPath resultPath) {
        int trivialCount = 0;
        String resultPathString = resultPath.toString();
        for (ResultPath trivialPath : trivialDFs) {
            String str = trivialPath.toString();
            if (resultPathString.startsWith(str)) {
                trivialCount++;
            }
        }
        return resultPath.getLevel() - trivialCount;
    }


    @Override
    public void fieldFetched(ExecutionContext executionContext, ExecutionStrategyParameters parameters, DataFetcher<?> dataFetcher, CompletableFuture<Object> fetchedValue) {
        ResultPath path = parameters.getPath();
        int level = getLevelForPath(path);
        System.out.println("field fetched at " + path);
        if (fetchedValue instanceof ChainedDataLoader) {
            ChainedDataLoader chainedDataLoader = (ChainedDataLoader) fetchedValue;
            System.out.println("found chained DL at " + path);
            stateLock.runLocked(() -> {
                levelToChainedDataLoaders.get(level).add(chainedDataLoader);
            });
        }

        if (dataFetcher instanceof TrivialDataFetcher) {
            // this means we want to act like this DF didn't really happen,
            // but we still wait for this level to be ready
            stateLock.runLocked(() -> {
                if (!levelToTrivialDataFetchersFetched.containsKey(level)) {
                    levelToTrivialDataFetchersFetched.put(level, ConcurrentHashMap.newKeySet());
                }
                levelToTrivialDataFetchersFetched.get(level).add(path);
            });
        } else {
            boolean dispatchNeeded = stateLock.callLocked(() -> {
                levelToHappenedFetchedFields.get(level).add(path);
                return isDispatchReady(level);
            });
            if (dispatchNeeded) {
                dispatch(level);
            }
        }
    }

    @Override
    public void fieldFetchedDone(ExecutionContext executionContext, ExecutionStrategyParameters parameters, DataFetcher<?> dataFetcher, Object value, GraphQLObjectType parentType, GraphQLFieldDefinition fieldDefinition) {
        ResultPath path = parameters.getPath();
        int level = getLevelForPath(parameters.getPath());
        GraphQLType fieldType = fieldDefinition.getType();
        GraphQLType unwrappedFieldType = unwrapAll(fieldType);
        Integer levelReadyForDispatch;
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
            levelReadyForDispatch = stateLock.callLocked(() -> {
                if (levelToTrivialDataFetchersFetched.containsKey(level) && levelToTrivialDataFetchersFetched.get(level).contains(path)) {
                    // record the current field as fetched,
                    // but add the children as expectation to the current level instead of the next
                    levelToHappenedFetchedFields.get(level).add(path);
                    levelToHappenedFetchedFieldDone.get(level).add(path);
                    levelToExpectedExecuteObject.get(level).addAll(executeObjectPaths);
                    trivialDFs.add(path);
                    return null;
                } else {
                    levelToHappenedFetchedFieldDone.get(level).add(path);
                    levelToExpectedExecuteObject.get(level + 1).addAll(executeObjectPaths);
                    return isDispatchReady(level + 1) ? level + 1 : null;
                }
            });
        } else {
            levelReadyForDispatch = stateLock.callLocked(() -> {
                if (levelToTrivialDataFetchersFetched.containsKey(level) && levelToTrivialDataFetchersFetched.get(level).contains(path)) {
                    // record the current field as fetched, there
                    // are no children to wait for. This means either
                    // the trivial DF returned null or is of type Scalar/Enum
                    levelToHappenedFetchedFields.get(level).add(path);
                    levelToHappenedFetchedFieldDone.get(level).add(path);
                    // when this happens we need to consider the current level to be
                    // ready additionally to the next level, because in fieldFetched
                    // we don't register the fetched as happened
                    if (isDispatchReady(level)) {
                        return level;
                    }
                    return isDispatchReady(level + 1) ? level + 1 : null;
                } else {
                    levelToHappenedFetchedFieldDone.get(level).add(path);
                }
                return isDispatchReady(level + 1) ? level + 1 : null;
            });
        }
        // the reason we check for the next level to be ready is
        // because done fetched fields are only relevant for the next level,
        // not the current one. See the checks in isDispatchReady
        if (levelReadyForDispatch != null) {
            dispatch(levelReadyForDispatch);
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


    private boolean isDispatchReady(int level) {
        if (level == 1) {
            // level 1 is special: there is only one strategy call and that's it
            return allFetchesHappened(1) && allExecuteObjectHappened(1);
        }
        if (isDispatchReady(level - 1) && parentLevelFieldsAreDone(level)
                && allExecuteObjectHappened(level) && allFetchesHappened(level)) {

            return true;
        }
//        System.out.println("NOT READY: " + level);
//        System.out.println("levelToExpectedExecuteObject" + levelToExpectedExecuteObject);
//        System.out.println("levelToHappenedExecuteObject" + levelToHappenedExecuteObject);
//        System.out.println("levelToExpectedFetchField" + levelToExpectedFetchField);
//        System.out.println("levelToHappenedFetchedFields" + levelToHappenedFetchedFields);
//        System.out.println("levelToHappenedFetchedFieldDone" + levelToHappenedFetchedFieldDone);
        return false;
    }


    private boolean allExecuteObjectHappened(int level) {
        return levelToExpectedExecuteObject.get(level).equals(levelToHappenedExecuteObject.get(level));
    }

    private boolean allFetchesHappened(int level) {
        return levelToExpectedFetchField.get(level).equals(levelToHappenedFetchedFields.get(level));
    }

    private boolean parentLevelFieldsAreDone(int level) {
        return levelToHappenedFetchedFieldDone.get(level - 1).equals(levelToHappenedFetchedFields.get(level - 1));
    }


    void dispatch(int level) {
        System.out.println("DISPATCH!! level : " + level);
        DataLoaderRegistry dataLoaderRegistry = executionContext.getDataLoaderRegistry();
        dataLoaderRegistry.dispatchAll();
        // once all first DataLoaders are finished an
        if (levelToChainedDataLoaders.containsKey(level)) {
            Set<ChainedDataLoader<?, ?>> chainedDataLoaders = levelToChainedDataLoaders.get(level);
            Async.CombinedBuilder<Void> futures = Async.ofExpectedSize(chainedDataLoaders.size());
            for (ChainedDataLoader<?, ?> chainedDataLoader : chainedDataLoaders) {
                futures.add(chainedDataLoader.getSecondDataLoaderCalled());
            }
            futures.await().thenAccept((voids) -> {
                System.out.println("CALLING DISPATCH AGAIN!");
                dataLoaderRegistry.dispatchAll();
            });
        }
    }


}
