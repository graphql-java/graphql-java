package graphql;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ExperimentalApi
public class ProfilerResult {

    public static String PROFILER_CONTEXT_KEY = "__GJ_PROFILER";

    private int fieldCount;

    private int propertyDataFetcherCount;

    private final Set<String> fieldsFetched = ConcurrentHashMap.newKeySet();

    public static enum ResultType {
        COMPLETABLE_FUTURE_COMPLETED,
        COMPLETABLE_FUTURE_NOT_COMPLETED,
        MATERIALIZED

    }

    private Map<String, ResultType> queryPathToResultType;


    public void addFieldFetched(String fieldPath) {
        fieldsFetched.add(fieldPath);
    }

    public Set<String> getFieldsFetched() {
        return fieldsFetched;
    }


}
