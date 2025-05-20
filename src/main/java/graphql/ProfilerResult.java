package graphql;

import graphql.execution.ExecutionId;

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
    private final Set<String> fieldsFetched = ConcurrentHashMap.newKeySet();

    private final AtomicInteger totalDataFetcherInvocations = new AtomicInteger();
    private final AtomicInteger totalPropertyDataFetcherInvocations = new AtomicInteger();


    private final Map<String, Integer> dataFetcherInvocationCount = new ConcurrentHashMap<>();
    private final Map<String, DataFetcherType> dataFetcherTypeMap = new ConcurrentHashMap<>();

    // the key is the whole result key, not just the query path
    private final Map<String, DataFetcherResultType> dataFetcherResultType = new ConcurrentHashMap<>();


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

    void setDataFetcherType(String key, DataFetcherType dataFetcherType) {
        dataFetcherTypeMap.putIfAbsent(key, dataFetcherType);
        totalDataFetcherInvocations.incrementAndGet();
        if (dataFetcherType == DataFetcherType.PROPERTY_DATA_FETCHER) {
            totalPropertyDataFetcherInvocations.incrementAndGet();
        }
    }

    void setDataFetcherResultType(String resultPath, DataFetcherResultType fetchedType) {
        dataFetcherResultType.put(resultPath, fetchedType);
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

    @Override
    public String toString() {
        return "ProfilerResult{" +
                "executionId=" + executionId +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", engineTotalRunningTime=" + engineTotalRunningTime +
                ", fieldsFetched=" + fieldsFetched +
                ", totalDataFetcherInvocations=" + totalDataFetcherInvocations +
                ", totalPropertyDataFetcherInvocations=" + totalPropertyDataFetcherInvocations +
                ", dataFetcherInvocationCount=" + dataFetcherInvocationCount +
                ", dataFetcherTypeMap=" + dataFetcherTypeMap +
                ", dataFetcherResultType=" + dataFetcherResultType +
                '}';
    }
}
