package graphql;

import graphql.execution.ExecutionId;
import graphql.language.OperationDefinition;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ExperimentalApi
public class ProfilerResult {

    public static final String PROFILER_CONTEXT_KEY = "__GJ_PROFILER";

    private volatile ExecutionId executionId;
    private long startTime;
    private long endTime;
    private long engineTotalRunningTime;
    private final AtomicInteger totalDataFetcherInvocations = new AtomicInteger();
    private final AtomicInteger totalPropertyDataFetcherInvocations = new AtomicInteger();
    private final Set<String> fieldsFetched = ConcurrentHashMap.newKeySet();


    private final Map<String, Integer> dataFetcherInvocationCount = new ConcurrentHashMap<>();
    private final Map<String, Integer> dataLoaderLoadInvocations = new ConcurrentHashMap<>();
    private final Map<String, DataFetcherType> dataFetcherTypeMap = new ConcurrentHashMap<>();

    private final Map<String, DataFetcherResultType> dataFetcherResultType = new ConcurrentHashMap<>();
    private volatile String operationName;
    private volatile String operationType;
    private volatile boolean dataLoaderChainingEnabled;
    private final Set<Integer> oldStrategyDispatchingAll = ConcurrentHashMap.newKeySet();
    private final Set<Integer> chainedStrategyDispatching = ConcurrentHashMap.newKeySet();


    public enum DataFetcherType {
        PROPERTY_DATA_FETCHER,
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
        if (dataFetcherType == DataFetcherType.PROPERTY_DATA_FETCHER) {
            totalPropertyDataFetcherInvocations.incrementAndGet();
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
        this.operationType = operationDefinition.getOperation().name();
    }

    void addDataLoaderUsed(String dataLoaderName) {
        dataLoaderLoadInvocations.compute(dataLoaderName, (k, v) -> v == null ? 1 : v + 1);
    }

    void oldStrategyDispatchingAll(int level) {
        oldStrategyDispatchingAll.add(level);
    }


    void chainedStrategyDispatching(int level) {
        chainedStrategyDispatching.add(level);
    }




    public String getOperationName() {
        return operationName;
    }

    public String getOperationType() {
        return operationType;
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

    public Set<String> getPropertyDataFetcherFields() {
        Set<String> result = new LinkedHashSet<>(fieldsFetched);
        for (String field : fieldsFetched) {
            if (dataFetcherTypeMap.get(field) == DataFetcherType.PROPERTY_DATA_FETCHER) {
                result.add(field);
            }
        }
        return result;
    }


    public int getTotalDataFetcherInvocations() {
        return totalDataFetcherInvocations.get();
    }

    public int getTotalPropertyDataFetcherInvocations() {
        return totalPropertyDataFetcherInvocations.get();
    }

    public int getTotalCustomDataFetcherInvocations() {
        return totalDataFetcherInvocations.get() - totalPropertyDataFetcherInvocations.get();
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

    public Set<Integer> getChainedStrategyDispatching() {
        return chainedStrategyDispatching;
    }

    public Set<Integer> getOldStrategyDispatchingAll() {
        return oldStrategyDispatchingAll;
    }

    public String fullSummary() {
        return "ProfilerResult{" +
                "executionId=" + executionId +
                ", operation=" + operationType + ":" + operationName +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", totalRunTime=" + (endTime - startTime) + "(" + (endTime - startTime) / 1_000_000 + "ms)" +
                ", engineTotalRunningTime=" + engineTotalRunningTime + "(" + engineTotalRunningTime / 1_000_000 + "ms)" +
                ", totalDataFetcherInvocations=" + totalDataFetcherInvocations +
                ", totalPropertyDataFetcherInvocations=" + totalPropertyDataFetcherInvocations +
                ", fieldsFetched=" + fieldsFetched +
                ", dataFetcherInvocationCount=" + dataFetcherInvocationCount +
                ", dataFetcherTypeMap=" + dataFetcherTypeMap +
                ", dataFetcherResultType=" + dataFetcherResultType +
                ", dataLoaderChainingEnabled=" + dataLoaderChainingEnabled +
                ", dataLoaderLoadInvocations=" + dataLoaderLoadInvocations +
                ", oldStrategyDispatchingAll=" + oldStrategyDispatchingAll +
                ", chainedStrategyDispatching" + chainedStrategyDispatching +
                '}';
    }

    public String shortSummary() {
        return "ProfilerResult{" +
                "executionId=" + executionId +
                ", operation=" + operationType + ":" + operationName +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", totalRunTime=" + (endTime - startTime) + "(" + (endTime - startTime) / 1_000_000 + "ms)" +
                ", engineTotalRunningTime=" + engineTotalRunningTime + "(" + engineTotalRunningTime / 1_000_000 + "ms)" +
                ", totalDataFetcherInvocations=" + totalDataFetcherInvocations +
                ", totalPropertyDataFetcherInvocations=" + totalPropertyDataFetcherInvocations +
                ", fieldsFetchedCount=" + fieldsFetched.size() +
                ", dataLoaderChainingEnabled=" + dataLoaderChainingEnabled +
                ", dataLoaderLoadInvocations=" + dataLoaderLoadInvocations +
                ", oldStrategyDispatchingAll=" + oldStrategyDispatchingAll +
                ", chainedStrategyDispatching" + chainedStrategyDispatching +
                '}';


    }

    @Override
    public String toString() {
        return shortSummary();
    }
}
