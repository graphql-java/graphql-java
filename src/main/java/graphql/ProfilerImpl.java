package graphql;

import graphql.execution.EngineRunningObserver;
import graphql.execution.ExecutionId;
import graphql.execution.ResultPath;
import graphql.language.OperationDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.PropertyDataFetcher;
import graphql.schema.SingletonPropertyDataFetcher;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

@Internal
@NullMarked
public class ProfilerImpl implements Profiler {

    private volatile long startTime;
    private volatile long endTime;
    private volatile long lastStartTime;
    private final AtomicLong engineTotalRunningTime = new AtomicLong();


    final ProfilerResult profilerResult = new ProfilerResult();

    public ProfilerImpl(GraphQLContext graphQLContext) {
        graphQLContext.put(ProfilerResult.PROFILER_CONTEXT_KEY, profilerResult);
    }

    @Override
    public void setExecutionId(ExecutionId executionId) {
        profilerResult.setExecutionId(executionId);
    }

    @Override
    public void fieldFetched(Object fetchedObject, DataFetcher<?> dataFetcher, ResultPath path) {
        String key = "/" + String.join("/", path.getKeysOnly());
        profilerResult.addFieldFetched(key);
        profilerResult.incrementDataFetcherInvocationCount(key);
        ProfilerResult.DataFetcherType dataFetcherType;
        if (dataFetcher instanceof PropertyDataFetcher || dataFetcher instanceof SingletonPropertyDataFetcher) {
            dataFetcherType = ProfilerResult.DataFetcherType.PROPERTY_DATA_FETCHER;
        } else {
            dataFetcherType = ProfilerResult.DataFetcherType.CUSTOM;
            // we only record the type of the result if it is not a PropertyDataFetcher
            ProfilerResult.DataFetcherResultType dataFetcherResultType;
            if (fetchedObject instanceof CompletableFuture) {
                CompletableFuture<?> completableFuture = (CompletableFuture<?>) fetchedObject;
                if (completableFuture.isDone()) {
                    dataFetcherResultType = ProfilerResult.DataFetcherResultType.COMPLETABLE_FUTURE_COMPLETED;
                } else {
                    dataFetcherResultType = ProfilerResult.DataFetcherResultType.COMPLETABLE_FUTURE_NOT_COMPLETED;
                }
            } else {
                dataFetcherResultType = ProfilerResult.DataFetcherResultType.MATERIALIZED;
            }
            profilerResult.setDataFetcherResultType(path.toString(), dataFetcherResultType);
        }

        profilerResult.setDataFetcherType(key, dataFetcherType);
    }

    @Override
    public EngineRunningObserver wrapEngineRunningObserver(EngineRunningObserver engineRunningObserver) {
        // nothing to wrap here
        return new EngineRunningObserver() {
            @Override
            public void runningStateChanged(ExecutionId executionId, GraphQLContext graphQLContext, RunningState runningState) {
                runningStateChangedImpl(executionId, graphQLContext, runningState);
                if (engineRunningObserver != null) {
                    engineRunningObserver.runningStateChanged(executionId, graphQLContext, runningState);
                }
            }
        };
    }

    private void runningStateChangedImpl(ExecutionId executionId, GraphQLContext graphQLContext, EngineRunningObserver.RunningState runningState) {
        long now = System.nanoTime();
        if (runningState == EngineRunningObserver.RunningState.RUNNING_START) {
            startTime = now;
            lastStartTime = startTime;
        } else if (runningState == EngineRunningObserver.RunningState.NOT_RUNNING_FINISH) {
            endTime = now;
            engineTotalRunningTime.set(engineTotalRunningTime.get() + (endTime - lastStartTime));
            profilerResult.setTimes(startTime, endTime, engineTotalRunningTime.get());
        } else if (runningState == EngineRunningObserver.RunningState.RUNNING) {
            lastStartTime = now;
        } else if (runningState == EngineRunningObserver.RunningState.NOT_RUNNING) {
            engineTotalRunningTime.set(engineTotalRunningTime.get() + (now - lastStartTime));
        } else {
            Assert.assertShouldNeverHappen();
        }
    }

    @Override
    public void operationDefinition(OperationDefinition operationDefinition) {
        profilerResult.setOperation(operationDefinition);
    }
}
