package graphql.execution.instrumentation.dataloader;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.NoOpInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.stats.Statistics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * This graphql {@link graphql.execution.instrumentation.Instrumentation} will dispatch
 * all the contained {@link org.dataloader.DataLoader}s when each level of the graphql
 * query is executed.
 *
 * This allows you to use {@link org.dataloader.DataLoader}s in your {@link graphql.schema.DataFetcher}s
 * to optimal loading of data.
 *
 * @see org.dataloader.DataLoader
 * @see org.dataloader.DataLoaderRegistry
 */
public class DataLoaderDispatcherInstrumentation extends NoOpInstrumentation {

    private final DataLoaderRegistry dataLoaderRegistry;
    private final DataLoaderDispatcherInstrumentationOptions options;

    /**
     * You pass in a registry of N data loaders which will be {@link org.dataloader.DataLoader#dispatch() dispatched} as
     * each level of the query executes.
     *
     * @param dataLoaderRegistry the registry of data loaders that will be dispatched
     */
    public DataLoaderDispatcherInstrumentation(DataLoaderRegistry dataLoaderRegistry) {
        this(dataLoaderRegistry, DataLoaderDispatcherInstrumentationOptions.newOptions());
    }

    /**
     * You pass in a registry of N data loaders which will be {@link org.dataloader.DataLoader#dispatch() dispatched} as
     * each level of the query executes.
     *
     * @param dataLoaderRegistry the registry of data loaders that will be dispatched
     * @param options            the options to control the behaviour
     */
    public DataLoaderDispatcherInstrumentation(DataLoaderRegistry dataLoaderRegistry, DataLoaderDispatcherInstrumentationOptions options) {
        this.dataLoaderRegistry = dataLoaderRegistry;
        this.options = options;
    }

    @Override
    public InstrumentationContext<CompletableFuture<ExecutionResult>> beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        return (result, t) -> {
            if (t == null) {
                // only dispatch when there are no errors
                dataLoaderRegistry.dispatchAll();
            }
        };
    }


    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
        if (!options.isIncludeStatistics()) {
            return CompletableFuture.completedFuture(executionResult);
        }
        Map<Object, Object> currentExt = executionResult.getExtensions();
        Map<Object, Object> statsMap = new LinkedHashMap<>();
        statsMap.putAll(currentExt == null ? Collections.emptyMap() : currentExt);
        statsMap.put("dataloader", buildStatsMap());

        return CompletableFuture.completedFuture(new ExecutionResultImpl(executionResult.getData(), executionResult.getErrors(), statsMap));
    }

    private Map<Object, Object> buildStatsMap() {
        Statistics allStats = dataLoaderRegistry.getStatistics();
        Map<Object, Object> statsMap = new LinkedHashMap<>();
        statsMap.put("overall-statistics", allStats.toMap());

        Map<Object, Object> individualStatsMap = new LinkedHashMap<>();

        for (String dlKey : dataLoaderRegistry.getKeys()) {
            DataLoader<Object, Object> dl = dataLoaderRegistry.getDataLoader(dlKey);
            Statistics statistics = dl.getStatistics();
            individualStatsMap.put(dlKey, statistics.toMap());
        }

        statsMap.put("individual-statistics", individualStatsMap);

        return statsMap;
    }
}