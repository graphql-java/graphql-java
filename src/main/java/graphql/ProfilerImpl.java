package graphql;

import graphql.execution.ResultPath;
import graphql.schema.DataFetcher;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Internal
@NullMarked
public class ProfilerImpl implements Profiler {

    volatile long startTime;
    volatile int rootFieldCount;

    AtomicInteger propertyDataFetcherCount;

    final Map<String, Integer> dataFetcherInvocationCount = new ConcurrentHashMap<>();


    final ProfilerResult profilerResult = new ProfilerResult();

    public ProfilerImpl(GraphQLContext graphQLContext) {
        graphQLContext.put(ProfilerResult.PROFILER_CONTEXT_KEY, profilerResult);
    }

    @Override
    public void start() {
        startTime = System.nanoTime();
    }


    @Override
    public void rootFieldCount(int count) {
        this.rootFieldCount = count;
    }

    @Override
    public void fieldFetched(Object fetchedObject, DataFetcher<?> dataFetcher, ResultPath path) {
        String key = String.join("/", path.getKeysOnly());
        profilerResult.addFieldFetched(key);

//        dataFetcherInvocationCount.compute(key, (k, v) -> v == null ? 1 : v + 1);
//
//        if (dataFetcher instanceof PropertyDataFetcher) {
//            propertyDataFetcherCount.incrementAndGet();
//        }
    }
}
