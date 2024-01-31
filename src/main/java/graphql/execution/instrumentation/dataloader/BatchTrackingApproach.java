package graphql.execution.instrumentation.dataloader;

import graphql.ExecutionResult;
import graphql.Internal;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.FieldValueInfo;
import graphql.execution.ResultPath;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.GraphQLType;
import graphql.util.LockKit;
import org.dataloader.DataLoaderRegistry;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static graphql.schema.GraphQLTypeUtil.isObjectType;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;

/**
 * This approach uses field level tracking to achieve its aims of making the data loader more efficient
 */
@Internal
public class BatchTrackingApproach {
    private final Supplier<DataLoaderRegistry> dataLoaderRegistrySupplier;


    private static class CallStack implements InstrumentationState {

        private final LockKit.ReentrantLock lock = new LockKit.ReentrantLock();

        private final LevelMap expectedFetchCountPerLevel = new LevelMap();
        private final LevelMap fetchCountPerLevel = new LevelMap();
        private final LevelMap expectedStrategyCallsPerLevel = new LevelMap();
        private final LevelMap happenedStrategyCallsPerLevel = new LevelMap();
        private final LevelMap happenedOnFieldValueCallsPerLevel = new LevelMap();

        private final Set<Integer> dispatchedLevels = new LinkedHashSet<>();
        private final Set<ResultPath> expectedFetches = new LinkedHashSet<>();
        private final Set<ResultPath> waitForChildren = new LinkedHashSet<>();
        private final Set<ResultPath> expectedStrategyCalls = new LinkedHashSet<>();

        CallStack() {
            expectedStrategyCallsPerLevel.set(1, 1);
        }

        void addExpectedStrategyCall(ResultPath path) {
            expectedStrategyCalls.add(path);
        }

        void addExpectedFetch(ResultPath path) {
            expectedFetches.add(path);
        }


        void waitForChildren(ResultPath path) {
            waitForChildren.add(path);
        }

        void removeWaitForChildren(ResultPath path) {
            waitForChildren.remove(path);
        }

        public LevelMap getExpectedFetchCountPerLevel() {
            return expectedFetchCountPerLevel;
        }

        void increaseExpectedFetchCount(int level, int count) {
            expectedFetchCountPerLevel.increment(level, count);
        }

        void increaseFetchCount(int level) {
            fetchCountPerLevel.increment(level, 1);
        }

        void increaseExpectedStrategyCalls(int level, int count) {
            expectedStrategyCallsPerLevel.increment(level, count);
        }

        void increaseHappenedStrategyCalls(int level) {
            happenedStrategyCallsPerLevel.increment(level, 1);
        }

        void increaseHappenedOnFieldValueCalls(int level) {
            happenedOnFieldValueCallsPerLevel.increment(level, 1);
        }

        boolean allStrategyCallsHappened(int level) {
            return happenedStrategyCallsPerLevel.get(level) == expectedStrategyCallsPerLevel.get(level);
        }

        boolean allOnFieldCallsHappened(int level) {
            return happenedOnFieldValueCallsPerLevel.get(level) == expectedStrategyCallsPerLevel.get(level);
        }

        boolean allFetchesHappened(int level) {
            return fetchCountPerLevel.get(level) == expectedFetchCountPerLevel.get(level);
        }

        @Override
        public String toString() {
            return "CallStack{" +
                "expectedFetchCountPerLevel=" + expectedFetchCountPerLevel +
                ", fetchCountPerLevel=" + fetchCountPerLevel +
                ", expectedStrategyCallsPerLevel=" + expectedStrategyCallsPerLevel +
                ", happenedStrategyCallsPerLevel=" + happenedStrategyCallsPerLevel +
                ", happenedOnFieldValueCallsPerLevel=" + happenedOnFieldValueCallsPerLevel +
                ", dispatchedLevels" + dispatchedLevels +
                '}';
        }


    }

    public BatchTrackingApproach(Supplier<DataLoaderRegistry> dataLoaderRegistrySupplier) {
        this.dataLoaderRegistrySupplier = dataLoaderRegistrySupplier;
    }

    public InstrumentationState createState() {
        return new CallStack();
    }


    public void completeValue(ExecutionContext executionContext,
                              Object value,
                              GraphQLType fieldType,
                              ExecutionStrategyParameters parameters,
                              InstrumentationState rawState) {
        CallStack callStack = (CallStack) rawState;
        ResultPath path = parameters.getExecutionStepInfo().getPath();
        ResultPath pathWithoutList = path;
        while (pathWithoutList.isListSegment()) {
            pathWithoutList = pathWithoutList.getParent();
        }
        ResultPath finalPathWithoutList = pathWithoutList;
        boolean waitingFor = callStack.lock.callLocked(() -> {
            return callStack.waitForChildren.contains(finalPathWithoutList);
        });
        if (!waitingFor) {
            return;
        }
        if (path == pathWithoutList) {
            if (value == null) {
                boolean dispatch = callStack.lock.callLocked(() -> {
                    callStack.removeWaitForChildren(path);
                    return isDispatchNeeded(callStack);
                });
                if (dispatch) {
                    dispatch();
                }
            }
            if (isObjectType(fieldType)) {
                callStack.lock.runLocked(() -> {
                    callStack.removeWaitForChildren(finalPathWithoutList);
                    callStack.addExpectedStrategyCall(path);
                });
            }
            return;
        }
        // means we are inside a list
        // means we are inside the "leafs" of the list
        if (value != null && isObjectType(fieldType)) {
            callStack.lock.runLocked(() -> {
                System.out.println("add expected Strategy call for " + path);
                callStack.addExpectedStrategyCall(path);
            });
        }

    }

    public void finishedListCompletion(ExecutionContext executionContext, ExecutionStrategyParameters parameters, InstrumentationState rawState, int count) {
        CallStack callStack = (CallStack) rawState;
        boolean dispatch = callStack.lock.callLocked(() -> {
            System.out.println("list finished: " + parameters.getPath());
            callStack.removeWaitForChildren(parameters.getPath());
            return isDispatchNeeded(callStack);
        });
        if (dispatch) {
            dispatch();
        }

    }

    ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters, InstrumentationState rawState) {
        CallStack callStack = (CallStack) rawState;
        ResultPath path = parameters.getExecutionStrategyParameters().getPath();
        int parentLevel = path.getLevel();
        int curLevel = parentLevel + 1;
        int fieldCount = parameters.getExecutionStrategyParameters().getFields().size();

        if (path == ResultPath.rootPath()) {
            callStack.lock.runLocked(() -> {
                parameters.getExecutionStrategyParameters().getFields().getKeys().forEach(key -> {
                    callStack.addExpectedFetch(path.segment(key));
                });
            });
            System.out.println("root fields expected: " + callStack.expectedFetches);
        } else {
            boolean expected = callStack.lock.callLocked(() -> {
                return callStack.expectedStrategyCalls.contains(path);
            });
            if (expected) {
                callStack.lock.runLocked(() -> {
                    parameters.getExecutionStrategyParameters().getFields().getKeys().forEach(key -> {
                        System.out.println("waiting for fetching of " + path.segment(key));
                        callStack.addExpectedFetch(path.segment(key));
                    });
                    callStack.expectedStrategyCalls.remove(path);
                });
            }
        }

        return new ExecutionStrategyInstrumentationContext() {
            @Override
            public void onDispatched(CompletableFuture<ExecutionResult> result) {

            }

            @Override
            public void onCompleted(ExecutionResult result, Throwable t) {

            }

            @Override
            public void onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) {
                // boolean dispatchNeeded = callStack.lock.callLocked(() ->
                //     handleOnFieldValuesInfo(fieldValueInfoList, callStack, curLevel)
                // );
                // if (dispatchNeeded) {
                //     dispatch();
                // }
            }

            @Override
            public void onFieldValuesException() {
                callStack.lock.runLocked(() ->
                    callStack.increaseHappenedOnFieldValueCalls(curLevel)
                );
            }
        };
    }

    //
    // thread safety : called with synchronised(callStack)
    //
    private boolean handleOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfos, CallStack callStack,
                                            int curLevel) {
        callStack.increaseHappenedOnFieldValueCalls(curLevel);
        int expectedStrategyCalls = getCountForList(fieldValueInfos);
        callStack.increaseExpectedStrategyCalls(curLevel + 1, expectedStrategyCalls);
        return isDispatchNeeded(callStack);
    }

    private int getCountForList(List<FieldValueInfo> fieldValueInfos) {
        int result = 0;
        for (FieldValueInfo fieldValueInfo : fieldValueInfos) {
            if (fieldValueInfo.getCompleteValueType() == FieldValueInfo.CompleteValueType.OBJECT) {
                result += 1;
            } else if (fieldValueInfo.getCompleteValueType() == FieldValueInfo.CompleteValueType.LIST) {
                result += getCountForList(fieldValueInfo.getFieldValueInfos());
            }
        }
        return result;
    }


    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters
                                                              parameters, InstrumentationState rawState) {
        CallStack callStack = (CallStack) rawState;
        ResultPath path = parameters.getEnvironment().getExecutionStepInfo().getPath();
        int level = path.getLevel();
        boolean isTrivialDF = parameters.isTrivialDataFetcher();

        // for trivial DF we also expect the children to be fetched before dispatching
        if (isTrivialDF && isObjectType(unwrapAll(parameters.getExecutionStepInfo().getType()))) {
            callStack.lock.runLocked(() -> {
                System.out.println("trivial DF: waiting for children for: " + path);
                callStack.waitForChildren(path);
            });
        }

        return new InstrumentationContext<>() {

            @Override
            public void onDispatched(CompletableFuture<Object> result) {
                // this is called after the data fetcher is being invoked
                // returning normally a CF
                boolean dispatchNeeded = callStack.lock.callLocked(() -> {
                    if (callStack.expectedFetches.remove(path)) {
                        return isDispatchNeeded(callStack);
                    } else {
                        return false;
                    }
                });
                if (dispatchNeeded) {
                    dispatch();
                }
            }

            @Override
            public void onCompleted(Object result, Throwable t) {
            }
        };
    }


    //
    // thread safety : called with synchronised(callStack)
    //
    private boolean isDispatchNeeded(CallStack callStack) {
        return callStack.expectedFetches.isEmpty() && callStack.waitForChildren.isEmpty() && callStack.expectedStrategyCalls.isEmpty();
    }

    //
    // thread safety : called with synchronised(callStack)
    //
    private boolean levelReady(CallStack callStack, int level) {
        if (level == 1) {
            // level 1 is special: there is only one strategy call and that's it
            return callStack.allFetchesHappened(1);
        }
        if (levelReady(callStack, level - 1) && callStack.allOnFieldCallsHappened(level - 1)
            && callStack.allStrategyCallsHappened(level) && callStack.allFetchesHappened(level)) {
            return true;
        }
        return false;
    }

    void dispatch() {
        System.out.println("Dispatch");
        DataLoaderRegistry dataLoaderRegistry = getDataLoaderRegistry();
        dataLoaderRegistry.dispatchAll();
    }

    private DataLoaderRegistry getDataLoaderRegistry() {
        return dataLoaderRegistrySupplier.get();
    }
}
