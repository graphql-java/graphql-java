package graphql.execution.instrumentation.dataloader;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategy;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.ExecuteObjectInstrumentationContext;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.language.OperationDefinition;
import graphql.schema.DataFetcher;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.stats.Statistics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static graphql.execution.instrumentation.InstrumentationState.ofState;
import static graphql.execution.instrumentation.SimpleInstrumentationContext.noOp;

/**
 * This graphql {@link graphql.execution.instrumentation.Instrumentation} will dispatch
 * all the contained {@link org.dataloader.DataLoader}s when each level of the graphql
 * query is executed.
 * <p>
 * This allows you to use {@link org.dataloader.DataLoader}s in your {@link graphql.schema.DataFetcher}s
 * to optimal loading of data.
 * <p>
 * A DataLoaderDispatcherInstrumentation will be automatically added to the {@link graphql.GraphQL}
 * instrumentation list if one is not present.
 *
 * @see org.dataloader.DataLoader
 * @see org.dataloader.DataLoaderRegistry
 */
@PublicApi
public class DataLoaderDispatcherInstrumentation extends SimplePerformantInstrumentation {
    private final DataLoaderDispatcherInstrumentationOptions options;

    /**
     * Creates a DataLoaderDispatcherInstrumentation with the default options
     */
    public DataLoaderDispatcherInstrumentation() {
        this(DataLoaderDispatcherInstrumentationOptions.newOptions());
    }

    /**
     * Creates a DataLoaderDispatcherInstrumentation with the specified options
     *
     * @param options the options to control the behaviour
     */
    public DataLoaderDispatcherInstrumentation(DataLoaderDispatcherInstrumentationOptions options) {
        this.options = options;
    }


    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return new DataLoaderDispatcherInstrumentationState(parameters.getExecutionInput().getDataLoaderRegistry());
    }

    @Override
    public @NotNull DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters, InstrumentationState rawState) {
        DataLoaderDispatcherInstrumentationState state = ofState(rawState);
        if (state.isAggressivelyBatching()) {
            return dataFetcher;
        }
        //
        // currently only AsyncExecutionStrategy with DataLoader and hence this allows us to "dispatch"
        // on every object if it's not using aggressive batching for other execution strategies
        // which allows them to work if used.
        return (DataFetcher<Object>) environment -> {
            Object obj = dataFetcher.get(environment);
            immediatelyDispatch(state);
            return obj;
        };
    }

    private void immediatelyDispatch(DataLoaderDispatcherInstrumentationState state) {
        state.getApproach().dispatch();
    }

    @Override
    public @Nullable InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters, InstrumentationState rawState) {
        DataLoaderDispatcherInstrumentationState state = ofState(rawState);
        //
        // during #instrumentExecutionInput they could have enhanced the data loader registry
        // so we grab it now just before the query operation gets started
        //
        DataLoaderRegistry finalRegistry = parameters.getExecutionContext().getDataLoaderRegistry();
        state.setDataLoaderRegistry(finalRegistry);
        if (!isDataLoaderCompatibleExecution(parameters.getExecutionContext())) {
            state.setAggressivelyBatching(false);
        }
        return noOp();
    }

    private boolean isDataLoaderCompatibleExecution(ExecutionContext executionContext) {
        //
        // Currently we only support aggressive batching for the AsyncExecutionStrategy.
        // This may change in the future but this is the fix for now.
        //
        OperationDefinition.Operation operation = executionContext.getOperationDefinition().getOperation();
        ExecutionStrategy strategy = executionContext.getStrategy(operation);
        return (strategy instanceof AsyncExecutionStrategy);
    }

    @Override
    public @Nullable ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters, InstrumentationState rawState) {
        DataLoaderDispatcherInstrumentationState state = ofState(rawState);
        //
        // if there are no data loaders, there is nothing to do
        //
        if (state.hasNoDataLoaders()) {
            return ExecutionStrategyInstrumentationContext.NOOP;
        }
        return state.getApproach().beginExecutionStrategy(parameters, state.getState());
    }

    @Override
    public @Nullable ExecuteObjectInstrumentationContext beginExecuteObject(InstrumentationExecutionStrategyParameters parameters, InstrumentationState rawState) {
        DataLoaderDispatcherInstrumentationState state = ofState(rawState);
        //
        // if there are no data loaders, there is nothing to do
        //
        if (state.hasNoDataLoaders()) {
            return ExecuteObjectInstrumentationContext.NOOP;
        }
        return state.getApproach().beginObjectResolution(parameters, state.getState());
    }

    @Override
    public @Nullable InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters, InstrumentationState rawState) {
        DataLoaderDispatcherInstrumentationState state = ofState(rawState);
        //
        // if there are no data loaders, there is nothing to do
        //
        if (state.hasNoDataLoaders()) {
            return noOp();
        }
        return state.getApproach().beginFieldFetch(parameters, state.getState());
    }

    @Override
    public @NotNull CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters, InstrumentationState rawState) {
        if (!options.isIncludeStatistics()) {
            return CompletableFuture.completedFuture(executionResult);
        }
        DataLoaderDispatcherInstrumentationState state = ofState(rawState);
        Map<Object, Object> currentExt = executionResult.getExtensions();
        Map<Object, Object> statsMap = new LinkedHashMap<>(currentExt == null ? ImmutableKit.emptyMap() : currentExt);
        Map<Object, Object> dataLoaderStats = buildStatsMap(state);
        statsMap.put("dataloader", dataLoaderStats);

        return CompletableFuture.completedFuture(new ExecutionResultImpl(executionResult.getData(), executionResult.getErrors(), statsMap));
    }

    private Map<Object, Object> buildStatsMap(DataLoaderDispatcherInstrumentationState state) {
        DataLoaderRegistry dataLoaderRegistry = state.getDataLoaderRegistry();
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
