package graphql;

import graphql.schema.DataFetcher;
import graphql.schema.PropertyDataFetcher;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.atomic.AtomicInteger;

@Internal
@NullMarked
public class ProfilerImpl implements Profiler {

    volatile long startTime;
    volatile int rootFieldCount;

    AtomicInteger fieldCount;
    AtomicInteger propertyDataFetcherCount;

    @Override
    public void start() {
        startTime = System.nanoTime();
    }


    @Override
    public void rootFieldCount(int count) {
        this.rootFieldCount = count;
    }

    @Override
    public void fieldFetched(Object fetchedObject, DataFetcher<?> dataFetcher) {
        fieldCount.incrementAndGet();
        if (dataFetcher instanceof PropertyDataFetcher) {
            propertyDataFetcherCount.incrementAndGet();
        }
    }
}
