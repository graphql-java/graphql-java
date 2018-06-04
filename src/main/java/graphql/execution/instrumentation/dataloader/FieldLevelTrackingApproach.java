package graphql.execution.instrumentation.dataloader;

import graphql.Assert;
import graphql.ExecutionResult;
import graphql.Internal;
import graphql.execution.ExecutionPath;
import graphql.execution.FieldValueInfo;
import graphql.execution.instrumentation.DeferredFieldInstrumentationContext;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.parameters.InstrumentationDeferredFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.language.Field;
import org.dataloader.DataLoaderRegistry;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * This approach uses field level tracking to achieve its aims of making the data loader more efficient
 */
@Internal
public class FieldLevelTrackingApproach {
    private final DataLoaderRegistry dataLoaderRegistry;
    private final Logger log;

    private static class CallStack extends DataLoaderDispatcherInstrumentationState {

        private final Map<Integer, Integer> expectedFetchCountPerLevel = new LinkedHashMap<>();
        private final Map<Integer, Integer> fetchCountPerLevel = new LinkedHashMap<>();
        private final Map<Integer, Integer> expectedStrategyCallsPerLevel = new LinkedHashMap<>();
        private final Map<Integer, Integer> happenedStrategyCallsPerLevel = new LinkedHashMap<>();
        private final Map<Integer, Integer> happenedOnFieldValueCallsPerLevel = new LinkedHashMap<>();


        private final Set<Integer> dispatchedLevels = new LinkedHashSet<>();

        private int deferredRootLevel;

        CallStack() {
            expectedStrategyCallsPerLevel.put(1, 1);
        }


        synchronized int increaseExpectedFetchCount(int level, int count) {
            expectedFetchCountPerLevel.put(level, expectedFetchCountPerLevel.getOrDefault(level, 0) + count);
            return expectedFetchCountPerLevel.get(level);
        }

        synchronized void increaseFetchCount(int level) {
            fetchCountPerLevel.put(level, fetchCountPerLevel.getOrDefault(level, 0) + 1);
        }

        synchronized void increaseExpectedStrategyCalls(int level, int count) {
            expectedStrategyCallsPerLevel.put(level, expectedStrategyCallsPerLevel.getOrDefault(level, 0) + count);
        }

        synchronized void increaseHappenedStrategyCalls(int level) {
            happenedStrategyCallsPerLevel.put(level, happenedStrategyCallsPerLevel.getOrDefault(level, 0) + 1);
        }

        synchronized void increaseHappenedOnFieldValueCalls(int level) {
            happenedOnFieldValueCallsPerLevel.put(level, happenedOnFieldValueCallsPerLevel.getOrDefault(level, 0) + 1);
        }

        synchronized boolean allStrategyCallsHappened(int level) {
            return Objects.equals(happenedStrategyCallsPerLevel.get(level), expectedStrategyCallsPerLevel.get(level));
        }

        synchronized boolean allOnFieldCallsHappened(int level) {
            return Objects.equals(happenedOnFieldValueCallsPerLevel.get(level), expectedStrategyCallsPerLevel.get(level));
        }

        synchronized boolean allFetchsHappened(int level) {
            return Objects.equals(fetchCountPerLevel.get(level), expectedFetchCountPerLevel.get(level));
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

        public synchronized void dispatchIfNotDispatchedBefore(int level, Runnable dispatch) {
            if (dispatchedLevels.contains(level)) {
                Assert.assertShouldNeverHappen("level " + level + " already dispatched");
                return;
            }
            dispatchedLevels.add(level);
            dispatch.run();
        }

        public void clearAndMarkCurrentLevelAsReady(int level) {
            expectedFetchCountPerLevel.clear();
            fetchCountPerLevel.clear();
            expectedStrategyCallsPerLevel.clear();
            happenedStrategyCallsPerLevel.clear();
            happenedOnFieldValueCallsPerLevel.clear();
            dispatchedLevels.clear();

            // make sure the level is ready
            expectedFetchCountPerLevel.put(level, 1);
            expectedStrategyCallsPerLevel.put(level, 1);
            happenedStrategyCallsPerLevel.put(level, 1);
        }
    }

    public FieldLevelTrackingApproach(Logger log, DataLoaderRegistry dataLoaderRegistry) {
        this.dataLoaderRegistry = dataLoaderRegistry;
        this.log = log;
    }

    public InstrumentationState createState() {
        return new CallStack();
    }

    ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        CallStack callStack = parameters.getInstrumentationState();
        ExecutionPath path = parameters.getExecutionStrategyParameters().getPath();
        int parentLevel = path.getLevel();
        int curLevel = parentLevel + 1;
        int fieldCount = parameters.getExecutionStrategyParameters().getFields().size();
        callStack.increaseExpectedFetchCount(curLevel, fieldCount);
        callStack.increaseHappenedStrategyCalls(curLevel);

        return new ExecutionStrategyInstrumentationContext() {
            @Override
            public void onDispatched(CompletableFuture<ExecutionResult> result) {

            }

            @Override
            public void onCompleted(ExecutionResult result, Throwable t) {

            }

            @Override
            public void onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) {
                handleOnFieldValuesInfo(fieldValueInfoList, callStack, curLevel);
            }

            @Override
            public void onDeferredField(List<Field> field) {
                // fake fetch count for this field
                callStack.increaseFetchCount(curLevel);
                dispatchIfNeeded(callStack, curLevel);
            }
        };
    }

    private void handleOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, CallStack callStack, int curLevel) {
        callStack.increaseHappenedOnFieldValueCalls(curLevel);
        int expectedStrategyCalls = 0;
        for (FieldValueInfo fieldValueInfo : fieldValueInfoList) {
            if (fieldValueInfo.getCompleteValueType() == FieldValueInfo.CompleteValueType.OBJECT) {
                expectedStrategyCalls++;
            } else if (fieldValueInfo.getCompleteValueType() == FieldValueInfo.CompleteValueType.LIST) {
                expectedStrategyCalls += getCountForList(fieldValueInfo);
            }
        }
        callStack.increaseExpectedStrategyCalls(curLevel + 1, expectedStrategyCalls);
        dispatchIfNeeded(callStack, curLevel + 1);
    }

    private int getCountForList(FieldValueInfo fieldValueInfo) {
        int result = 0;
        for (FieldValueInfo cvi : fieldValueInfo.getFieldValueInfos()) {
            if (cvi.getCompleteValueType() == FieldValueInfo.CompleteValueType.OBJECT) {
                result++;
            } else if (cvi.getCompleteValueType() == FieldValueInfo.CompleteValueType.LIST) {
                result += getCountForList(cvi);
            }
        }
        return result;
    }

    DeferredFieldInstrumentationContext beginDeferredField(InstrumentationDeferredFieldParameters parameters) {
        CallStack callStack = parameters.getInstrumentationState();
        int level = parameters.getExecutionStrategyParameters().getPath().getLevel();
        callStack.clearAndMarkCurrentLevelAsReady(level);

        return new DeferredFieldInstrumentationContext() {
            @Override
            public void onDispatched(CompletableFuture<ExecutionResult> result) {

            }

            @Override
            public void onCompleted(ExecutionResult result, Throwable t) {
            }

            @Override
            public void onFieldValueInfo(FieldValueInfo fieldValueInfo) {
                handleOnFieldValuesInfo(Collections.singletonList(fieldValueInfo), callStack, level);
            }
        };
    }

    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        CallStack callStack = parameters.getInstrumentationState();
        ExecutionPath path = parameters.getEnvironment().getFieldTypeInfo().getPath();
        int level = path.getLevel();
        return new InstrumentationContext<Object>() {

            @Override
            public void onDispatched(CompletableFuture result) {
                callStack.increaseFetchCount(level);
                dispatchIfNeeded(callStack, level);
            }

            @Override
            public void onCompleted(Object result, Throwable t) {
            }
        };
    }


    private void dispatchIfNeeded(CallStack callStack, int level) {
        if (levelReady(callStack, level)) {
            callStack.dispatchIfNotDispatchedBefore(level, this::dispatch);
        }
    }

    private boolean levelReady(CallStack callStack, int level) {
        if (level == 1) {
            // level 1 is special: there is only one strategy call and that's it
            return callStack.allFetchsHappened(1);
        }
        if (levelReady(callStack, level - 1) && callStack.allOnFieldCallsHappened(level - 1)
                && callStack.allStrategyCallsHappened(level) && callStack.allFetchsHappened(level)) {
            return true;
        }
        return false;
    }

    void dispatch() {
        log.debug("Dispatching data loaders ({})", dataLoaderRegistry.getKeys());
        dataLoaderRegistry.dispatchAll();
    }
}
