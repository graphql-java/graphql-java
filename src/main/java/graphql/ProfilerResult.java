package graphql;

import graphql.execution.ExecutionId;
import graphql.language.OperationDefinition;
import graphql.language.OperationDefinition.Operation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ExperimentalApi
@NullMarked
public class ProfilerResult {

    public static final String PROFILER_CONTEXT_KEY = "__GJ_PROFILER";

    @Nullable
    private volatile ExecutionId executionId;
    private long startTime;
    private long endTime;
    private long engineTotalRunningTime;
    private final AtomicInteger totalDataFetcherInvocations = new AtomicInteger();
    private final AtomicInteger totalTrivialDataFetcherInvocations = new AtomicInteger();
    private final AtomicInteger totalWrappedTrivialDataFetcherInvocations = new AtomicInteger();

    // this is the count of how many times a data loader was invoked per data loader name
    private final Map<String, Integer> dataLoaderLoadInvocations = new ConcurrentHashMap<>();

    @Nullable
    private volatile String operationName;
    @Nullable
    private volatile Operation operationType;
    private volatile boolean dataLoaderChainingEnabled;
    private final Set<Integer> oldStrategyDispatchingAll = ConcurrentHashMap.newKeySet();

    private final List<String> instrumentationClasses = Collections.synchronizedList(new ArrayList<>());

    private final List<DispatchEvent> dispatchEvents = Collections.synchronizedList(new ArrayList<>());

    /**
     * the following fields can contain a lot of data for large requests
     */
    // all fields fetched during the execution, key is the field path
    private final Set<String> fieldsFetched = ConcurrentHashMap.newKeySet();
    // this is the count of how many times a data fetcher was invoked per field
    private final Map<String, Integer> dataFetcherInvocationCount = new ConcurrentHashMap<>();
    // the type of the data fetcher per field, key is the field path
    private final Map<String, DataFetcherType> dataFetcherTypeMap = new ConcurrentHashMap<>();
    // the type of the data fetcher result field, key is the field path
    // in theory different DataFetcher invocations can return different types, but we only record the first one
    private final Map<String, DataFetcherResultType> dataFetcherResultType = new ConcurrentHashMap<>();

    public void setInstrumentationClasses(List<String> instrumentationClasses) {
        this.instrumentationClasses.addAll(instrumentationClasses);
    }


    public enum DispatchEventType {
        STRATEGY_DISPATCH,
        MANUAL_DISPATCH,
    }

    public static class DispatchEvent {
        final String dataLoaderName;
        final @Nullable Integer level; // is null for delayed dispatching
        final int keyCount; // how many
        final DispatchEventType type;

        public DispatchEvent(String dataLoaderName, @Nullable Integer level, int keyCount, DispatchEventType type) {
            this.dataLoaderName = dataLoaderName;
            this.level = level;
            this.keyCount = keyCount;
            this.type = type;
        }

        public String getDataLoaderName() {
            return dataLoaderName;
        }

        public @Nullable Integer getLevel() {
            return level;
        }

        public int getKeyCount() {
            return keyCount;
        }

        public DispatchEventType getType() {
            return type;
        }

        @Override
        public String toString() {
            return "DispatchEvent{" +
                    "type=" + type +
                    ", dataLoaderName='" + dataLoaderName + '\'' +
                    ", level=" + level +
                    ", keyCount=" + keyCount +
                    '}';
        }
    }

    public enum DataFetcherType {
        WRAPPED_TRIVIAL_DATA_FETCHER,
        TRIVIAL_DATA_FETCHER,
        CUSTOM
    }

    public enum DataFetcherResultType {
        COMPLETABLE_FUTURE_COMPLETED,
        COMPLETABLE_FUTURE_NOT_COMPLETED,
        MATERIALIZED

    }


    // setters are package private to prevent exposure

    void setDataLoaderChainingEnabled(boolean dataLoaderChainingEnabled) {
        this.dataLoaderChainingEnabled = dataLoaderChainingEnabled;
    }


    void setDataFetcherType(String key, DataFetcherType dataFetcherType) {
        dataFetcherTypeMap.putIfAbsent(key, dataFetcherType);
        totalDataFetcherInvocations.incrementAndGet();
        if (dataFetcherType == DataFetcherType.TRIVIAL_DATA_FETCHER) {
            totalTrivialDataFetcherInvocations.incrementAndGet();
        } else if (dataFetcherType == DataFetcherType.WRAPPED_TRIVIAL_DATA_FETCHER) {
            totalWrappedTrivialDataFetcherInvocations.incrementAndGet();
        }
    }

    void setDataFetcherResultType(String key, DataFetcherResultType fetchedType) {
        dataFetcherResultType.putIfAbsent(key, fetchedType);
    }

    void incrementDataFetcherInvocationCount(String key) {
        dataFetcherInvocationCount.compute(key, (k, v) -> v == null ? 1 : v + 1);
    }

    void addFieldFetched(String fieldPath) {
        fieldsFetched.add(fieldPath);
    }

    void setExecutionId(ExecutionId executionId) {
        this.executionId = executionId;
    }

    void setTimes(long startTime, long endTime, long engineTotalRunningTime) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.engineTotalRunningTime = engineTotalRunningTime;
    }

    void setOperation(OperationDefinition operationDefinition) {
        this.operationName = operationDefinition.getName();
        this.operationType = operationDefinition.getOperation();
    }

    void addDataLoaderUsed(String dataLoaderName) {
        dataLoaderLoadInvocations.compute(dataLoaderName, (k, v) -> v == null ? 1 : v + 1);
    }

    void oldStrategyDispatchingAll(int level) {
        oldStrategyDispatchingAll.add(level);
    }


    void addDispatchEvent(String dataLoaderName, @Nullable Integer level, int count, DispatchEventType type) {
        dispatchEvents.add(new DispatchEvent(dataLoaderName, level, count, type));
    }

    // public getters

    public @Nullable String getOperationName() {
        return operationName;
    }

    public Operation getOperationType() {
        return Assert.assertNotNull(operationType);
    }

    public Set<String> getFieldsFetched() {
        return fieldsFetched;
    }

    public Set<String> getCustomDataFetcherFields() {
        Set<String> result = new LinkedHashSet<>(fieldsFetched);
        for (String field : fieldsFetched) {
            if (dataFetcherTypeMap.get(field) == DataFetcherType.CUSTOM) {
                result.add(field);
            }
        }
        return result;
    }

    public Set<String> getTrivialDataFetcherFields() {
        Set<String> result = new LinkedHashSet<>(fieldsFetched);
        for (String field : fieldsFetched) {
            if (dataFetcherTypeMap.get(field) == DataFetcherType.TRIVIAL_DATA_FETCHER) {
                result.add(field);
            }
        }
        return result;
    }


    public int getTotalDataFetcherInvocations() {
        return totalDataFetcherInvocations.get();
    }

    public int getTotalTrivialDataFetcherInvocations() {
        return totalTrivialDataFetcherInvocations.get();
    }

    public int getTotalCustomDataFetcherInvocations() {
        return totalDataFetcherInvocations.get() - totalTrivialDataFetcherInvocations.get() - totalWrappedTrivialDataFetcherInvocations.get();
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public long getEngineTotalRunningTime() {
        return engineTotalRunningTime;
    }

    public long getTotalExecutionTime() {
        return endTime - startTime;
    }

    public Map<String, DataFetcherResultType> getDataFetcherResultType() {
        return dataFetcherResultType;
    }

    public Map<String, Integer> getDataLoaderLoadInvocations() {
        return dataLoaderLoadInvocations;
    }


    public Set<Integer> getOldStrategyDispatchingAll() {
        return oldStrategyDispatchingAll;
    }

    public boolean isDataLoaderChainingEnabled() {
        return dataLoaderChainingEnabled;
    }

    public List<DispatchEvent> getDispatchEvents() {
        return dispatchEvents;
    }

    public List<String> getInstrumentationClasses() {
        return instrumentationClasses;
    }


    public Map<String, Object> shortSummaryMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executionId", Assert.assertNotNull(executionId));
        result.put("operation", operationType + ":" + operationName);
        result.put("startTime", startTime);
        result.put("endTime", endTime);
        result.put("totalRunTime", (endTime - startTime) + "(" + (endTime - startTime) / 1_000_000 + "ms)");
        result.put("engineTotalRunningTime", engineTotalRunningTime + "(" + engineTotalRunningTime / 1_000_000 + "ms)");
        result.put("totalDataFetcherInvocations", totalDataFetcherInvocations);
        result.put("totalCustomDataFetcherInvocations", getTotalCustomDataFetcherInvocations());
        result.put("totalTrivialDataFetcherInvocations", totalTrivialDataFetcherInvocations);
        result.put("totalWrappedTrivialDataFetcherInvocations", totalWrappedTrivialDataFetcherInvocations);
        result.put("fieldsFetchedCount", fieldsFetched.size());
        result.put("dataLoaderChainingEnabled", dataLoaderChainingEnabled);
        result.put("dataLoaderLoadInvocations", dataLoaderLoadInvocations);
        result.put("oldStrategyDispatchingAll", oldStrategyDispatchingAll);
        result.put("dispatchEvents", getDispatchEventsAsMap());
        result.put("instrumentationClasses", instrumentationClasses);
        int completedCount = 0;
        int completedInvokeCount = 0;
        int notCompletedCount = 0;
        int notCompletedInvokeCount = 0;
        int materializedCount = 0;
        int materializedInvokeCount = 0;
        for (String field : dataFetcherResultType.keySet()) {
            DataFetcherResultType dFRT = dataFetcherResultType.get(field);
            if (dFRT == DataFetcherResultType.COMPLETABLE_FUTURE_COMPLETED) {
                completedInvokeCount += Assert.assertNotNull(dataFetcherInvocationCount.get(field));
                completedCount++;
            } else if (dFRT == DataFetcherResultType.COMPLETABLE_FUTURE_NOT_COMPLETED) {
                notCompletedInvokeCount += Assert.assertNotNull(dataFetcherInvocationCount.get(field));
                notCompletedCount++;
            } else if (dFRT == DataFetcherResultType.MATERIALIZED) {
                materializedInvokeCount += Assert.assertNotNull(dataFetcherInvocationCount.get(field));
                materializedCount++;
            } else {
                Assert.assertShouldNeverHappen();
            }
        }
        result.put("dataFetcherResultTypes", Map.of(
                DataFetcherResultType.COMPLETABLE_FUTURE_COMPLETED.name(), "(count:" + completedCount + ", invocations:" + completedInvokeCount + ")",
                DataFetcherResultType.COMPLETABLE_FUTURE_NOT_COMPLETED.name(), "(count:" + notCompletedCount + ", invocations:" + notCompletedInvokeCount + ")",
                DataFetcherResultType.MATERIALIZED.name(), "(count:" + materializedCount + ", invocations:" + materializedInvokeCount + ")"
        ));
        return result;
    }


    private String printDispatchEvents() {
        if (dispatchEvents.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int i = 0;
        for (DispatchEvent event : dispatchEvents) {
            sb.append("dataLoader=")
                    .append(event.getDataLoaderName())
                    .append(", level=")
                    .append(event.getLevel())
                    .append(", count=").append(event.getKeyCount());
            if (i++ < dispatchEvents.size() - 1) {
                sb.append("; ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public List<Map<String, Object>> getDispatchEventsAsMap() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DispatchEvent event : dispatchEvents) {
            Map<String, Object> eventMap = new LinkedHashMap<>();
            eventMap.put("type", event.getType().name());
            eventMap.put("dataLoader", event.getDataLoaderName());
            eventMap.put("level", event.getLevel() != null ? event.getLevel() : "delayed");
            eventMap.put("keyCount", event.getKeyCount());
            result.add(eventMap);
        }
        return result;
    }

    @Override
    public String toString() {
        return "ProfilerResult" + shortSummaryMap();
    }

}
