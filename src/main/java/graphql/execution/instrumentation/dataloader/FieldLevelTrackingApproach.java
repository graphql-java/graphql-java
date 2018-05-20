package graphql.execution.instrumentation.dataloader;

import graphql.ExecutionResult;
import graphql.execution.CompleteValueInfo;
import graphql.execution.ExecutionPath;
import graphql.execution.instrumentation.ExecutionStrategyContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.parameters.InstrumentationDeferredFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import org.dataloader.DataLoaderRegistry;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static graphql.execution.instrumentation.SimpleInstrumentationContext.whenDispatched;

/**
 * This approach uses field level tracking to achieve its aims of making the data loader more efficient
 */
public class FieldLevelTrackingApproach {
    private final DataLoaderRegistry dataLoaderRegistry;
    private final Logger log;

    private static class CallStack extends DataLoaderDispatcherInstrumentationState {

        private final Map<Integer, Integer> expectedFetchCountPerLevel = new LinkedHashMap<>();
        private final Map<Integer, Integer> fetchCountPerLevel = new LinkedHashMap<>();
        private final Map<Integer, Integer> expectedStrategyCallsPerLevel = new LinkedHashMap<>();
        private final Map<Integer, Integer> happenedStrategyCallsPerLevel = new LinkedHashMap<>();


        private int lastDispatchedLevel;

        CallStack() {
            expectedStrategyCallsPerLevel.put(1, 1);
        }

        synchronized int increaseExpectedFetchCount(int level, int count) {
            expectedFetchCountPerLevel.put(level, expectedFetchCountPerLevel.getOrDefault(level, 0) + count);
            return expectedFetchCountPerLevel.get(level);
        }

        synchronized void increaseFetchCount(int level, int count) {
            fetchCountPerLevel.put(level, fetchCountPerLevel.getOrDefault(level, 0) + count);
        }

        synchronized void increaseExpectedStrategyCalls(int level, int count) {
            expectedStrategyCallsPerLevel.put(level, expectedStrategyCallsPerLevel.getOrDefault(level, 0) + count);
        }

        synchronized void increaseHappenedStrategyCalls(int level, int count) {
            happenedStrategyCallsPerLevel.put(level, happenedStrategyCallsPerLevel.getOrDefault(level, 0) + count);
        }

        synchronized boolean allStrategyCallsHappened(int level) {
            return Objects.equals(happenedStrategyCallsPerLevel.get(level), expectedStrategyCallsPerLevel.get(level));
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
                    '}';
        }

        public synchronized void markAsDispatched(int level) {
            if (lastDispatchedLevel + 1 != level) {
                System.err.println("lastDispatched Level : " + lastDispatchedLevel + " but tyring to dispatch " + level);
            }
            lastDispatchedLevel++;
        }
    }

    public FieldLevelTrackingApproach(Logger log, DataLoaderRegistry dataLoaderRegistry) {
        this.dataLoaderRegistry = dataLoaderRegistry;
        this.log = log;
    }

    public InstrumentationState createState() {
        return new CallStack();
    }

    InstrumentationContext<ExecutionResult> beginExecuteOperation() {
        return whenDispatched((result) -> {
        });
    }

    ExecutionStrategyContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        CallStack callStack = parameters.getInstrumentationState();
        ExecutionPath path = parameters.getExecutionStrategyParameters().getPath();
        int parentLevel = path.getLevel();
        int curLevel = parentLevel + 1;
        int fieldCount = parameters.getExecutionStrategyParameters().getFields().size();
        callStack.increaseExpectedFetchCount(curLevel, fieldCount);

        return new ExecutionStrategyContext() {
            @Override
            public void onDispatched(CompletableFuture<ExecutionResult> result) {

            }

            @Override
            public void onCompleted(ExecutionResult result, Throwable t) {

            }

            @Override
            public void completeValuesInfo(List<CompleteValueInfo> completeValueInfoList) {
                callStack.increaseHappenedStrategyCalls(curLevel, 1);
                int expectedStrategyCalls = 0;
                for (CompleteValueInfo completeValueInfo : completeValueInfoList) {
                    if (completeValueInfo.getCompleteValueType() == CompleteValueInfo.CompleteValueType.OBJECT) {
                        expectedStrategyCalls++;
                    } else if (completeValueInfo.getCompleteValueType() == CompleteValueInfo.CompleteValueType.LIST) {
                        expectedStrategyCalls += getCountForList(completeValueInfo);
                    }
                }
                callStack.increaseExpectedStrategyCalls(curLevel + 1, expectedStrategyCalls);
                dispatchIfNeeded(callStack, curLevel );
            }
        };
    }

    private int getCountForList(CompleteValueInfo completeValueInfo) {
        int result = 0;
        for (CompleteValueInfo cvi : completeValueInfo.getListInfos()) {
            if (cvi.getCompleteValueType() == CompleteValueInfo.CompleteValueType.OBJECT) {
                result++;
            } else if (cvi.getCompleteValueType() == CompleteValueInfo.CompleteValueType.LIST) {
                result += getCountForList(cvi);
            }
        }
        return result;
    }

    InstrumentationContext<ExecutionResult> beginDeferredField(InstrumentationDeferredFieldParameters parameters) {
        CallStack callStack = parameters.getInstrumentationState();
        int targetLevel = parameters.getTypeInfo().getPath().getLevel();
        // with deferred fields we aggressively dispatch the data loaders because the neat hierarchy of
        // outstanding fields and so on is lost because the field has jumped out of the execution tree
        return whenDispatched((result) -> dispatchIfNeeded(callStack, targetLevel));
    }

    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        CallStack callStack = parameters.getInstrumentationState();
        ExecutionPath path = parameters.getEnvironment().getFieldTypeInfo().getPath();
        int level = path.getLevel();
        return new InstrumentationContext<Object>() {

            @Override
            public void onDispatched(CompletableFuture result) {
                callStack.increaseFetchCount(level, 1);
                dispatchIfNeeded(callStack, level);
            }

            @Override
            public void onCompleted(Object result, Throwable t) {
            }
        };
    }


    private void dispatchIfNeeded(CallStack callStack, int level) {
        if (levelReady(callStack, level)) {
            callStack.markAsDispatched(level);
            dispatch();
        }
    }

    private boolean levelReady(CallStack callStack, int level) {
        if (level == 0) {
            return true;
        }
        if (levelReady(callStack, level - 1) && callStack.allStrategyCallsHappened(level) && callStack.allFetchsHappened(level)) {
            return true;
        }
        return false;
    }

    void dispatch() {
        log.debug("Dispatching data loaders ({})", dataLoaderRegistry.getKeys());
        System.out.println("Dispatching data loaders " + dataLoaderRegistry.getKeys());
        dataLoaderRegistry.dispatchAll();
    }
}
