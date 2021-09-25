package graphql.execution.instrumentation.dataloader;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.PublicApi;
import graphql.execution.Async;
import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategy;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
public class DataLoaderDispatcherInstrumentation extends SimpleInstrumentation {

    private static final Logger log = LoggerFactory.getLogger(DataLoaderDispatcherInstrumentation.class);

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
        return new DataLoaderDispatcherInstrumentationState(log, parameters.getExecutionInput().getDataLoaderRegistry());
    }

    @Override
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
        DataLoaderDispatcherInstrumentationState state = parameters.getInstrumentationState();
        if (state.isAggressivelyBatching()) {
            return dataFetcher;
        }
        //
        // currently only AsyncExecutionStrategy with DataLoader and hence this allows us to "dispatch"
        // on every object if its not using aggressive batching for other execution strategies
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
    public InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters) {
        DataLoaderDispatcherInstrumentationState state = parameters.getInstrumentationState();
        //
        // during #instrumentExecutionInput they could have enhanced the data loader registry
        // so we grab it now just before the query operation gets started
        //
        DataLoaderRegistry finalRegistry = parameters.getExecutionContext().getDataLoaderRegistry();
        state.setDataLoaderRegistry(finalRegistry);
        if (!isDataLoaderCompatibleExecution(parameters.getExecutionContext())) {
            state.setAggressivelyBatching(false);
        }
        return new SimpleInstrumentationContext<>();
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
    public ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        DataLoaderDispatcherInstrumentationState state = parameters.getInstrumentationState();
        //
        // if there are no data loaders, there is nothing to do
        //
        if (state.hasNoDataLoaders()) {
            return new ExecutionStrategyInstrumentationContext() {
                @Override
                public void onDispatched(CompletableFuture<ExecutionResult> result) {
                }

                @Override
                public void onCompleted(ExecutionResult result, Throwable t) {
                }
            };

        }
        return state.getApproach().beginExecutionStrategy(parameters.withNewState(state.getState()));
    }


    @Override
    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        DataLoaderDispatcherInstrumentationState state = parameters.getInstrumentationState();
        //
        // if there are no data loaders, there is nothing to do
        //
        if (state.hasNoDataLoaders()) {
            return new SimpleInstrumentationContext<>();
        }
        return state.getApproach().beginFieldFetch(parameters.withNewState(state.getState()));
    }

    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
        if (!options.isIncludeStatistics()) {
            return CompletableFuture.completedFuture(executionResult);
        }
        DataLoaderDispatcherInstrumentationState state = parameters.getInstrumentationState();
        Map<Object, Object> currentExt = executionResult.getExtensions();
        Map<Object, Object> statsMap = new LinkedHashMap<>(currentExt == null ? Collections.emptyMap() : currentExt);
        Map<Object, Object> dataLoaderStats = buildStatsMap(state);
        statsMap.put("dataloader", dataLoaderStats);

        if (log.isDebugEnabled()) {
            log.debug("Data loader stats : {}", dataLoaderStats);
        }

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
