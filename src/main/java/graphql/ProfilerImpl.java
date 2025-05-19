package graphql;

import graphql.execution.ResultPath;
import graphql.schema.DataFetcher;
import graphql.schema.PropertyDataFetcher;
import graphql.schema.SingletonPropertyDataFetcher;
import org.jspecify.annotations.NullMarked;

@Internal
@NullMarked
public class ProfilerImpl implements Profiler {

    volatile long startTime;


    final ProfilerResult profilerResult = new ProfilerResult();

    public ProfilerImpl(GraphQLContext graphQLContext) {
        graphQLContext.put(ProfilerResult.PROFILER_CONTEXT_KEY, profilerResult);
    }

    @Override
    public void start() {
        startTime = System.nanoTime();
    }

    @Override
    public void fieldFetched(Object fetchedObject, DataFetcher<?> dataFetcher, ResultPath path) {
        String key = String.join("/", path.getKeysOnly());
        profilerResult.addFieldFetched(key);
        profilerResult.incrementDataFetcherInvocationCount(key);
        ProfilerResult.DataFetcherType dataFetcherType;
        if (dataFetcher instanceof PropertyDataFetcher || dataFetcher instanceof SingletonPropertyDataFetcher) {
            dataFetcherType = ProfilerResult.DataFetcherType.PROPERTY_DATA_FETCHER;
        } else {
            dataFetcherType = ProfilerResult.DataFetcherType.CUSTOM;
        }
        profilerResult.setDataFetcherType(key, dataFetcherType);
    }
}
