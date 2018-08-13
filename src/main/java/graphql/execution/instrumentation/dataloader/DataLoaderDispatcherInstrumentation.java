package graphql.execution.instrumentation.dataloader;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategy;
import graphql.execution.instrumentation.DeferredFieldInstrumentationContext;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationDeferredFieldParameters;
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
import java.util.function.Supplier;

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
public class DataLoaderDispatcherInstrumentation extends SimpleInstrumentation {

    private static final Logger log = LoggerFactory.getLogger(DataLoaderDispatcherInstrumentation.class);

    private final DataLoaderDispatcherInstrumentationOptions options;
    private final Supplier<DataLoaderRegistry> supplier;

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
        this(() -> dataLoaderRegistry, options);
    }
    
    /**
     * You pass in supplier of a registry of N data loaders which will be {@link org.dataloader.DataLoader#dispatch() dispatched} as
     * each level of the query executes.
     *
     * @param supplier the supplier of registry of data loaders that will be dispatched
     */
    public DataLoaderDispatcherInstrumentation(Supplier<DataLoaderRegistry> supplier) {
        this(supplier, DataLoaderDispatcherInstrumentationOptions.newOptions());
    }
    
    /**
     * You pass in a supplier of registry of N data loaders which will be {@link org.dataloader.DataLoader#dispatch() dispatched} as
     * each level of the query executes.
     *
     * @param dataLoaderRegistry the registry of data loaders that will be dispatched
     * @param options            the options to control the behaviour
     */
    public DataLoaderDispatcherInstrumentation(Supplier<DataLoaderRegistry> supplier, DataLoaderDispatcherInstrumentationOptions options) {
        this.supplier = supplier;
        this.options = options;
    }


    @Override
    public InstrumentationState createState() {
        return new DataLoaderDispatcherInstrumentationState(log, supplier.get());
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
        if (!isDataLoaderCompatibleExecution(parameters.getExecutionContext())) {
            DataLoaderDispatcherInstrumentationState state = parameters.getInstrumentationState();
            state.setAggressivelyBatching(false);
        }
        return new SimpleInstrumentationContext<>();
    }

    private boolean isDataLoaderCompatibleExecution(ExecutionContext executionContext) {
        //
        // currently we only support Query operations and ONLY with AsyncExecutionStrategy as the query ES
        // This may change in the future but this is the fix for now
        //
        if (executionContext.getOperationDefinition().getOperation() == OperationDefinition.Operation.QUERY) {
            ExecutionStrategy queryStrategy = executionContext.getQueryStrategy();
            if (queryStrategy instanceof AsyncExecutionStrategy) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        DataLoaderDispatcherInstrumentationState state = parameters.getInstrumentationState();
        return state.getApproach().beginExecutionStrategy(parameters.withNewState(state.getState()));
    }

    @Override
    public DeferredFieldInstrumentationContext beginDeferredField(InstrumentationDeferredFieldParameters parameters) {
        DataLoaderDispatcherInstrumentationState state = parameters.getInstrumentationState();
        return state.getApproach().beginDeferredField(parameters.withNewState(state.getState()));
    }

    @Override
    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        DataLoaderDispatcherInstrumentationState state = parameters.getInstrumentationState();
        return state.getApproach().beginFieldFetch(parameters.withNewState(state.getState()));
    }

    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
        if (!options.isIncludeStatistics()) {
            return CompletableFuture.completedFuture(executionResult);
        }
        DataLoaderDispatcherInstrumentationState state = parameters.getInstrumentationState();
        Map<Object, Object> currentExt = executionResult.getExtensions();
        Map<Object, Object> statsMap = new LinkedHashMap<>();
        statsMap.putAll(currentExt == null ? Collections.emptyMap() : currentExt);
        Map<Object, Object> dataLoaderStats = buildStatsMap(state);
        statsMap.put("dataloader", dataLoaderStats);

        log.debug("Data loader stats : {}", dataLoaderStats);

        return CompletableFuture.completedFuture(new ExecutionResultImpl(executionResult.getData(), executionResult.getErrors(), statsMap));
    }

    private Map<Object, Object> buildStatsMap(DataLoaderDispatcherInstrumentationState state ) {
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