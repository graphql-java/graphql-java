package graphql.execution.instrumentation.dataloader;

import graphql.Assert;
import graphql.execution.ExecutionId;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class RequestStack {

    private final Map<ExecutionId, CallStack> activeRequests = new LinkedHashMap<>();

    private final Map<ExecutionId, Boolean> status = new LinkedHashMap<>();

    private static class CallStack {

        private final Map<Integer, Integer> expectedFetchCountPerLevel = new LinkedHashMap<>();
        private final Map<Integer, Integer> fetchCountPerLevel = new LinkedHashMap<>();
        private final Map<Integer, Integer> expectedStrategyCallsPerLevel = new LinkedHashMap<>();
        private final Map<Integer, Integer> happenedStrategyCallsPerLevel = new LinkedHashMap<>();
        private final Map<Integer, Integer> happenedOnFieldValueCallsPerLevel = new LinkedHashMap<>();


        private final Set<Integer> dispatchedLevels = new LinkedHashSet<>();

        CallStack() {
            expectedStrategyCallsPerLevel.put(1, 1);
        }


        public int increaseExpectedFetchCount(int level, int count) {
            expectedFetchCountPerLevel.put(level, expectedFetchCountPerLevel.getOrDefault(level, 0) + count);
            return expectedFetchCountPerLevel.get(level);
        }

        public void increaseFetchCount(int level) {
            fetchCountPerLevel.put(level, fetchCountPerLevel.getOrDefault(level, 0) + 1);
        }

        public void increaseExpectedStrategyCalls(int level, int count) {
            expectedStrategyCallsPerLevel.put(level, expectedStrategyCallsPerLevel.getOrDefault(level, 0) + count);
        }

        public void increaseHappenedStrategyCalls(int level) {
            happenedStrategyCallsPerLevel.put(level, happenedStrategyCallsPerLevel.getOrDefault(level, 0) + 1);
        }

        void increaseHappenedOnFieldValueCalls(int level) {
            happenedOnFieldValueCallsPerLevel.put(level, happenedOnFieldValueCallsPerLevel.getOrDefault(level, 0) + 1);
        }

        boolean allStrategyCallsHappened(int level) {
            return Objects.equals(happenedStrategyCallsPerLevel.get(level), expectedStrategyCallsPerLevel.get(level));
        }

        boolean allOnFieldCallsHappened(int level) {
            return Objects.equals(happenedOnFieldValueCallsPerLevel.get(level), expectedStrategyCallsPerLevel.get(level));
        }

        boolean allFetchesHappened(int level) {
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

        public boolean dispatchIfNotDispatchedBefore(int level) {
            if (dispatchedLevels.contains(level)) {
                Assert.assertShouldNeverHappen("level " + level + " already dispatched");
                return false;
            }
            dispatchedLevels.add(level);
            return true;
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

    public void setStatus(ExecutionId executionId, boolean toState) {
        status.put(executionId, toState);
    }

    public boolean allReady() {
        return !status.values().stream().anyMatch(Boolean.FALSE::equals);
    }

    public void allReset() {
        status.clear();
    }

    public boolean contains(ExecutionId executionId) {
        return activeRequests.containsKey(executionId);
    }

    public void removeExecution(ExecutionId executionId) {
        activeRequests.remove(executionId);
        status.remove(executionId);
    }

    public void addExecution(ExecutionId executionId) {
        CallStack callStack = new CallStack();
        status.put(executionId, false);
        activeRequests.put(executionId, callStack);
    }

    public void increaseExpectedFetchCount(ExecutionId executionId, int curLevel, int fieldCount) {
        activeRequests.get(executionId).increaseExpectedFetchCount(curLevel, fieldCount);
    }

    public void increaseHappenedStrategyCalls(ExecutionId executionId, int curLevel) {
        activeRequests.get(executionId).increaseHappenedStrategyCalls(curLevel);
    }

    public void increaseHappenedOnFieldValueCalls(ExecutionId executionId, int curLevel) {
        activeRequests.get(executionId).increaseHappenedOnFieldValueCalls(curLevel);
    }

    public void increaseExpectedStrategyCalls(ExecutionId executionId, int curLevel, int expectedStrategyCalls) {
        activeRequests.get(executionId).increaseExpectedStrategyCalls(curLevel, expectedStrategyCalls);
    }

    public boolean allFetchesHappened(ExecutionId executionId, int level) {
        return activeRequests.get(executionId).allFetchesHappened(level);
    }

    public boolean allOnFieldCallsHappened(ExecutionId executionId, int level) {
        return activeRequests.get(executionId).allOnFieldCallsHappened(level);
    }

    public boolean allStrategyCallsHappened(ExecutionId executionId, int level) {
        return activeRequests.get(executionId).allStrategyCallsHappened(level);
    }

    public boolean dispatchIfNotDispatchedBefore(ExecutionId executionId, int level) {
        return activeRequests.get(executionId).dispatchIfNotDispatchedBefore(level);
    }

    public void increaseFetchCount(ExecutionId executionId, int level) {
        activeRequests.get(executionId).increaseFetchCount(level);
    }

    public void clearAndMarkCurrentLevelAsReady(ExecutionId executionId, int level) {
        activeRequests.get(executionId).clearAndMarkCurrentLevelAsReady(level);
    }
}