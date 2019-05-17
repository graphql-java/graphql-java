package graphql.execution.instrumentation.dataloader;

import graphql.Assert;
import graphql.execution.ExecutionId;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 *  Manages sets of call stack state for ongoing executions.
 */
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

        private CallStack() {
            expectedStrategyCallsPerLevel.put(1, 1);
        }


        private void increaseExpectedFetchCount(int level, int count) {
            expectedFetchCountPerLevel.put(level, expectedFetchCountPerLevel.getOrDefault(level, 0) + count);
        }

        private void increaseFetchCount(int level) {
            fetchCountPerLevel.put(level, fetchCountPerLevel.getOrDefault(level, 0) + 1);
        }

        private void increaseExpectedStrategyCalls(int level, int count) {
            expectedStrategyCallsPerLevel.put(level, expectedStrategyCallsPerLevel.getOrDefault(level, 0) + count);
        }

        private void increaseHappenedStrategyCalls(int level) {
            happenedStrategyCallsPerLevel.put(level, happenedStrategyCallsPerLevel.getOrDefault(level, 0) + 1);
        }

        private void increaseHappenedOnFieldValueCalls(int level) {
            happenedOnFieldValueCallsPerLevel.put(level, happenedOnFieldValueCallsPerLevel.getOrDefault(level, 0) + 1);
        }

        private boolean allStrategyCallsHappened(int level) {
            return Objects.equals(happenedStrategyCallsPerLevel.get(level), expectedStrategyCallsPerLevel.get(level));
        }

        private boolean allOnFieldCallsHappened(int level) {
            return Objects.equals(happenedOnFieldValueCallsPerLevel.get(level), expectedStrategyCallsPerLevel.get(level));
        }

        private boolean allFetchesHappened(int level) {
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

        private boolean dispatchIfNotDispatchedBefore(int level) {
            if (dispatchedLevels.contains(level)) {
                Assert.assertShouldNeverHappen("level " + level + " already dispatched");
                return false;
            }
            dispatchedLevels.add(level);
            return true;
        }

        private void clearAndMarkCurrentLevelAsReady(int level) {
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

    /**
     * Sets the status indicating if a specific execution is ready for dispatching.
     * @param executionId must be an active execution
     * @param toState if ready to dispatch
     */
    public void setStatus(ExecutionId executionId, boolean toState) {
        if (!activeRequests.containsKey(executionId)) {
            throw new IllegalStateException(
                String.format("Can not set status for execution %s, it is not managed by this request stack", executionId));
        }
        status.put(executionId, toState);
    }

    /**
     * @return if all managed executions are ready to be dispatched.
     */
    public boolean allReady() {
        return status.values().stream().noneMatch(Boolean.FALSE::equals);
    }

    /**
     * Removes all dispatch status. Should be used after a call to dispatch.
     */
    public void allReset() {
        status.clear();
    }

    /**
     * Returns if this RequestStack is managing an execution for the supplied id.
     * @param executionId no restrictions
     * @return if an active execution
     */
    public boolean contains(ExecutionId executionId) {
        return activeRequests.containsKey(executionId);
    }

    /**
     * Removes any state associated with an id.
     * @param executionId no restrictions
     */
    public void removeExecution(ExecutionId executionId) {
        activeRequests.remove(executionId);
        status.remove(executionId);
    }

    /**
     * Creates a call stack for an associated id.
     * @param executionId can not already be managed by this RequestStack
     */
    public void addExecution(ExecutionId executionId) {
        if (activeRequests.containsKey(executionId)) {
            throw new IllegalStateException(String.format("An execution already exists for %s, can not create one", executionId));
        }
        CallStack callStack = new CallStack();
        status.put(executionId, false);
        activeRequests.put(executionId, callStack);
    }

    /**
     * Increases the expected fetch count for an execution.
     * @param executionId must be managed by this RequestStack
     * @param curLevel the level to increase the expected count
     * @param fieldCount the amount to increase the expected amount
     */
    public void increaseExpectedFetchCount(ExecutionId executionId, int curLevel, int fieldCount) {
        if (!activeRequests.containsKey(executionId)) {
            throw new IllegalStateException(
                String.format("Execution %s not managed by this RequestStack, can not increase expected fetch count", executionId));
        }
        activeRequests.get(executionId).increaseExpectedFetchCount(curLevel, fieldCount);
    }

    /**
     * Increments happened strategy calls for an execution at specified level.
     * @param executionId must be managed by this RequestStack
     * @param curLevel level to increment
     */
    public void increaseHappenedStrategyCalls(ExecutionId executionId, int curLevel) {
        if (!activeRequests.containsKey(executionId)) {
            throw new IllegalStateException(
                String.format("Execution %s not managed by this RequestStack, can not increase happened happened strategy calls", executionId));
        }
        activeRequests.get(executionId).increaseHappenedStrategyCalls(curLevel);
    }

    /**
     * Increments happened on field value calls for an execution at a specified level.
     * @param executionId must be managed by this RequestStack
     * @param curLevel level to increment
     */
    public void increaseHappenedOnFieldValueCalls(ExecutionId executionId, int curLevel) {
        if (!activeRequests.containsKey(executionId)) {
            throw new IllegalStateException(
                String.format("Execution %s not managed by this RequestStack, can not increase happened on field calls", executionId));
        }
        activeRequests.get(executionId).increaseHappenedOnFieldValueCalls(curLevel);
    }

    /**
     * Increases expected strategy calls for an execution at a specified level.
     * @param executionId must be managed by this RequestStack
     * @param curLevel level to increase
     * @param expectedStrategyCalls number to increment by
     */
    public void increaseExpectedStrategyCalls(ExecutionId executionId, int curLevel, int expectedStrategyCalls) {
        if (!activeRequests.containsKey(executionId)) {
            throw new IllegalStateException(
                String.format("Execution %s not managed by this RequestStack, can not increase expected strategy calls", executionId));
        }
        activeRequests.get(executionId).increaseExpectedStrategyCalls(curLevel, expectedStrategyCalls);
    }

    /**
     * Get the all fetches happened value for an execution at a specific level.
     * @param executionId must be managed by this RequestStack
     * @param level the level to get the value of
     * @return allFetchesHappened
     */
    public boolean allFetchesHappened(ExecutionId executionId, int level) {
        if (!activeRequests.containsKey(executionId)) {
            throw new IllegalStateException(
                String.format("Execution %s not managed by this RequestStack, can not get all fetches happened value", executionId));
        }
        return activeRequests.get(executionId).allFetchesHappened(level);
    }

    /**
     *  Get the all on field calls happened for an exectuion at a specific level.
     * @param executionId must be managed by this RequestStack
     * @param level the level to get the value of
     * @return allOnFieldCallsHappened
     */
    public boolean allOnFieldCallsHappened(ExecutionId executionId, int level) {
        if (!activeRequests.containsKey(executionId)) {
            throw new IllegalStateException(
                String.format("Execution %s not managed by this RequestStack, can not get all on field calls happened value", executionId));
        }
        return activeRequests.get(executionId).allOnFieldCallsHappened(level);
    }

    /**
     * Get the all strategy calls happened value for an exectuion at a specific level.
     * @param executionId must be managed by this RequestStack
     * @param level the level to get the value of
     * @return allStrategyCallsHappened
     */
    public boolean allStrategyCallsHappened(ExecutionId executionId, int level) {
        if (!activeRequests.containsKey(executionId)) {
            throw new IllegalStateException(
                String.format("Execution %s not managed by this RequestStack, can not get all strategy calls happened value", executionId));
        }
        return activeRequests.get(executionId).allStrategyCallsHappened(level);
    }

    /**
     * Get the dispatch if not dispatched before value of a specific level.
     * @param executionId must be managed by this RequestStack
     * @param level the level to get the value of
     * @return dispatchIfNotDispattchedBefore
     */
    public boolean dispatchIfNotDispatchedBefore(ExecutionId executionId, int level) {
        if (!activeRequests.containsKey(executionId)) {
            throw new IllegalStateException(
                String.format("Execution %s not managed by this RequestStack, can not get dispatch if not dispatched before value", executionId));
        }
        return activeRequests.get(executionId).dispatchIfNotDispatchedBefore(level);
    }

    /**
     * Increment the fetch count for an execution at a specific level.
     * @param executionId must be managed by this RequestStack
     * @param level the level to increment
     */
    public void increaseFetchCount(ExecutionId executionId, int level) {
        if (!activeRequests.containsKey(executionId)) {
            throw new IllegalStateException(String.format("Execution %s not managed by this RequestStack, can not increase fetch count", executionId));
        }
        activeRequests.get(executionId).increaseFetchCount(level);
    }

    /**
     * Clear and mark current level as ready for an execution.
     * @param executionId must be managed by this RequestStack
     * @param level the level to clear and mark
     */
    public void clearAndMarkCurrentLevelAsReady(ExecutionId executionId, int level) {
        if (!activeRequests.containsKey(executionId)) {
            throw new IllegalStateException(
                String.format("Execution %s not managed by this RequestStack, can not clea and mark current level as ready", executionId));
        }
        activeRequests.get(executionId).clearAndMarkCurrentLevelAsReady(level);
    }
}