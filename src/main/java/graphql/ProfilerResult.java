package graphql;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ExperimentalApi
public class ProfilerResult {

    public static String PROFILER_CONTEXT_KEY = "__GJ_PROFILER";

    private final AtomicInteger totalDataFetcherInvocations = new AtomicInteger();
    private final AtomicInteger totalPropertyDataFetcherInvocations = new AtomicInteger();


    private final Set<String> fieldsFetched = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> dataFetcherInvocationCount = new ConcurrentHashMap<>();
    private final Map<String, DataFetcherType> dataFetcherTypeMap = new ConcurrentHashMap<>();

    public enum DataFetcherType {
        PROPERTY_DATA_FETCHER,
        CUSTOM
    }

    public enum ResultType {
        COMPLETABLE_FUTURE_COMPLETED,
        COMPLETABLE_FUTURE_NOT_COMPLETED,
        MATERIALIZED

    }

    private Map<String, ResultType> queryPathToResultType;

    void setDataFetcherType(String key, DataFetcherType dataFetcherType) {
        dataFetcherTypeMap.putIfAbsent(key, dataFetcherType);
        totalDataFetcherInvocations.incrementAndGet();
        if (dataFetcherType == DataFetcherType.PROPERTY_DATA_FETCHER) {
            totalPropertyDataFetcherInvocations.incrementAndGet();
        }
    }

    void incrementDataFetcherInvocationCount(String key) {
        dataFetcherInvocationCount.compute(key, (k, v) -> v == null ? 1 : v + 1);
    }

    void addFieldFetched(String fieldPath) {
        fieldsFetched.add(fieldPath);
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

}
