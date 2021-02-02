package graphql.execution.instrumentation.dataloader;

import graphql.Assert;
import graphql.ExecutionResult;
import graphql.Internal;
import graphql.execution.FieldValueInfo;
import graphql.execution.ResultPath;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import org.dataloader.DataLoaderRegistry;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * This approach uses field level tracking to achieve its aims of making the data loader more efficient
 */
@Internal
public class FieldLevelTrackingApproach {
    private final Supplier<DataLoaderRegistry> dataLoaderRegistrySupplier;
    private final Logger log;

    private static class FieldTracking {

        final AtomicInteger expectedFetchCountPerLevel = new AtomicInteger();
        final AtomicInteger fetchCountPerLevel = new AtomicInteger();
        final AtomicInteger expectedStrategyCallsPerLevel = new AtomicInteger();
        final AtomicInteger happenedStrategyCallsPerLevel = new AtomicInteger();
        final AtomicInteger happenedOnFieldValueCallsPerLevel = new AtomicInteger();
    }

    private static class CallStack implements InstrumentationState {

        private final ConcurrentMap<Integer, FieldTracking> fieldTrackingPerLevel = new ConcurrentHashMap<>();
        private final ConcurrentHashMap.KeySetView<Integer, Boolean> dispatchedLevels = ConcurrentHashMap.newKeySet();

        CallStack() {
            FieldTracking fieldTracking = new FieldTracking();
            fieldTracking.expectedStrategyCallsPerLevel.incrementAndGet();
            fieldTrackingPerLevel.put(1, fieldTracking);
        }

        private FieldTracking provideFieldTracking(int level) {
            FieldTracking fieldTracking = fieldTrackingPerLevel.get(level);
            if (fieldTracking == null) {
                fieldTracking = fieldTrackingPerLevel.computeIfAbsent(level, ignored -> new FieldTracking());
            }
            return fieldTracking;
        }

        void increaseExpectedFetchCount(int level, int count) {
            provideFieldTracking(level).expectedFetchCountPerLevel.addAndGet(count);
        }

        void increaseFetchCount(int level) {
            provideFieldTracking(level).fetchCountPerLevel.addAndGet(1);
        }

        void increaseExpectedStrategyCalls(int level, int count) {
            provideFieldTracking(level).expectedStrategyCallsPerLevel.addAndGet(count);
        }

        void increaseHappenedStrategyCalls(int level) {
            provideFieldTracking(level).happenedStrategyCallsPerLevel.addAndGet(1);
        }

        void increaseHappenedOnFieldValueCalls(int level) {
            provideFieldTracking(level).happenedOnFieldValueCallsPerLevel.addAndGet(1);
        }

        boolean allStrategyCallsHappened(int level) {
            FieldTracking fieldTracking = provideFieldTracking(level);
            return Objects.equals(fieldTracking.happenedStrategyCallsPerLevel.get(), fieldTracking.expectedStrategyCallsPerLevel.get());
        }

        boolean allOnFieldCallsHappened(int level) {
            FieldTracking fieldTracking = provideFieldTracking(level);
            return Objects.equals(fieldTracking.happenedOnFieldValueCallsPerLevel.get(), fieldTracking.expectedStrategyCallsPerLevel.get());
        }

        boolean allFetchesHappened(int level) {
            FieldTracking fieldTracking = provideFieldTracking(level);
            return Objects.equals(fieldTracking.fetchCountPerLevel.get(), fieldTracking.expectedFetchCountPerLevel.get());
        }

        public boolean dispatchIfNotDispatchedBefore(int level) {
            if (dispatchedLevels.contains(level)) {
                Assert.assertShouldNeverHappen("level " + level + " already dispatched");
                return false;
            }
            dispatchedLevels.add(level);
            return true;
        }

	    @Override
	    public String toString() {
		    return "CallStack{" +
			    "fieldTrackingPerLevel=" + fieldTrackingPerLevel +
			    ", dispatchedLevels=" + dispatchedLevels +
			    '}';
	    }
    }

    public FieldLevelTrackingApproach(Logger log, Supplier<DataLoaderRegistry> dataLoaderRegistrySupplier) {
        this.dataLoaderRegistrySupplier = dataLoaderRegistrySupplier;
        this.log = log;
    }

    public InstrumentationState createState() {
        return new CallStack();
    }

    ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        CallStack callStack = parameters.getInstrumentationState();
        ResultPath path = parameters.getExecutionStrategyParameters().getPath();
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
                boolean dispatchNeeded = handleOnFieldValuesInfo(fieldValueInfoList, callStack, curLevel);
                if (dispatchNeeded) {
                    dispatch();
                }
            }
        };
    }

    private boolean handleOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, CallStack callStack, int curLevel) {
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
        return dispatchIfNeeded(callStack, curLevel + 1);
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


    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        CallStack callStack = parameters.getInstrumentationState();
        ResultPath path = parameters.getEnvironment().getExecutionStepInfo().getPath();
        int level = path.getLevel();

        return new InstrumentationContext<Object>() {

            @Override
            public void onDispatched(CompletableFuture<Object> result) {
                callStack.increaseFetchCount(level);
                boolean dispatchNeeded = dispatchIfNeeded(callStack, level);

                if (dispatchNeeded) {
                    dispatch();
                }

            }

            @Override
            public void onCompleted(Object result, Throwable t) {
            }
        };
    }

    private boolean dispatchIfNeeded(CallStack callStack, int level) {
        if (levelReady(callStack, level)) {
            return callStack.dispatchIfNotDispatchedBefore(level);
        }
        return false;
    }

    private boolean levelReady(CallStack callStack, int level) {
        if (level == 1) {
            // level 1 is special: there is only one strategy call and that's it
            return callStack.allFetchesHappened(1);
        }
        return levelReady(callStack, level - 1) && callStack.allOnFieldCallsHappened(level - 1)
            && callStack.allStrategyCallsHappened(level) && callStack.allFetchesHappened(level);
    }

    void dispatch() {
        DataLoaderRegistry dataLoaderRegistry = getDataLoaderRegistry();
        if (log.isDebugEnabled()) {
            log.debug("Dispatching data loaders ({})", dataLoaderRegistry.getKeys());
        }
        dataLoaderRegistry.dispatchAll();
    }

    private DataLoaderRegistry getDataLoaderRegistry() {
        return dataLoaderRegistrySupplier.get();
    }
}
