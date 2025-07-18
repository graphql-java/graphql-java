package graphql;

import graphql.execution.EngineRunningObserver;
import graphql.execution.ExecutionId;
import graphql.execution.ResultPath;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatchingContextKeys;
import graphql.language.OperationDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.PropertyDataFetcher;
import graphql.schema.SingletonPropertyDataFetcher;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
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
        // No real work can happen here, since the engine didn't "officially" start yet.
        graphQLContext.put(ProfilerResult.PROFILER_CONTEXT_KEY, profilerResult);
    }

    @Override
    public void setExecutionInputAndInstrumentation(ExecutionInput executionInput, Instrumentation instrumentation) {
        profilerResult.setExecutionId(executionInput.getExecutionIdNonNull());
        boolean dataLoaderChainingEnabled = executionInput.getGraphQLContext().getBoolean(DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING, false);
        profilerResult.setDataLoaderChainingEnabled(dataLoaderChainingEnabled);

        List<String> instrumentationClasses = new ArrayList<>();
        collectInstrumentationClasses(instrumentationClasses, instrumentation);
        profilerResult.setInstrumentationClasses(instrumentationClasses);
    }

    private void collectInstrumentationClasses(List<String> result, Instrumentation instrumentation) {
        if (instrumentation instanceof ChainedInstrumentation) {
            ChainedInstrumentation chainedInstrumentation = (ChainedInstrumentation) instrumentation;
            for (Instrumentation child : chainedInstrumentation.getInstrumentations()) {
                collectInstrumentationClasses(result, child);
            }
        } else {
            result.add(instrumentation.getClass().getName());
        }
    }


    @Override
    public void fieldFetched(Object fetchedObject, DataFetcher<?> originalDataFetcher, DataFetcher<?> dataFetcher, ResultPath path) {
        String key = "/" + String.join("/", path.getKeysOnly());
        profilerResult.addFieldFetched(key);
        profilerResult.incrementDataFetcherInvocationCount(key);
        ProfilerResult.DataFetcherType dataFetcherType;
        if (dataFetcher instanceof PropertyDataFetcher || dataFetcher instanceof SingletonPropertyDataFetcher) {
            dataFetcherType = ProfilerResult.DataFetcherType.TRIVIAL_DATA_FETCHER;
        } else if (originalDataFetcher instanceof PropertyDataFetcher || originalDataFetcher instanceof SingletonPropertyDataFetcher) {
            dataFetcherType = ProfilerResult.DataFetcherType.WRAPPED_TRIVIAL_DATA_FETCHER;
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
            profilerResult.setDataFetcherResultType(key, dataFetcherResultType);
        }

        profilerResult.setDataFetcherType(key, dataFetcherType);
    }

    @Override
    public EngineRunningObserver wrapEngineRunningObserver(@Nullable EngineRunningObserver engineRunningObserver) {
        // nothing to wrap here
        return new EngineRunningObserver() {
            @Override
            public void runningStateChanged(@Nullable ExecutionId executionId, GraphQLContext graphQLContext, RunningState runningState) {
                runningStateChangedImpl(executionId, graphQLContext, runningState);
                if (engineRunningObserver != null) {
                    engineRunningObserver.runningStateChanged(executionId, graphQLContext, runningState);
                }
            }
        };
    }

    private void runningStateChangedImpl(@Nullable ExecutionId executionId, GraphQLContext graphQLContext, EngineRunningObserver.RunningState runningState) {
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

    @Override
    public void dataLoaderUsed(String dataLoaderName) {
        profilerResult.addDataLoaderUsed(dataLoaderName);
    }

    @Override
    public void oldStrategyDispatchingAll(int level) {
        profilerResult.oldStrategyDispatchingAll(level);
    }

    @Override
    public void batchLoadedOldStrategy(String name, int level, int count) {
        profilerResult.addDispatchEvent(name, level, count, ProfilerResult.DispatchEventType.STRATEGY_DISPATCH);
    }

    @Override
    public void batchLoadedNewStrategy(String dataLoaderName, @Nullable Integer level, int count) {
        profilerResult.addDispatchEvent(dataLoaderName, level, count, ProfilerResult.DispatchEventType.STRATEGY_DISPATCH);
    }

    @Override
    public <V> void manualDispatch(String dataLoaderName, int level, int count) {
        profilerResult.addDispatchEvent(dataLoaderName, level, count, ProfilerResult.DispatchEventType.MANUAL_DISPATCH);
    }
}
