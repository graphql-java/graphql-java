package graphql.execution.instrumentation.dataloader;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.ExecutionStrategy;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.NoOpInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationDataFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.language.Field;
import graphql.schema.DataFetcher;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.stats.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
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

    private static final Logger log = LoggerFactory.getLogger(DataLoaderDispatcherInstrumentation.class);

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


    /**
     * We need to become stateful about whether we are in a list or not
     */
    private static class CallStack implements InstrumentationState {
        private boolean aggressivelyBatching = true;
        private final Deque<Boolean> stack = new ArrayDeque<>();

        private boolean isAggressivelyBatching() {
            return aggressivelyBatching;
        }

        private void setAggressivelyBatching(boolean aggressivelyBatching) {
            this.aggressivelyBatching = aggressivelyBatching;
        }

        private void enterList() {
            synchronized (this) {
                stack.push(true);
            }
        }

        private void exitList() {
            synchronized (this) {
                if (!stack.isEmpty()) {
                    stack.pop();
                }
            }
        }

        private boolean isInList() {
            synchronized (this) {
                if (stack.isEmpty()) {
                    return false;
                } else {
                    return stack.peek();
                }
            }
        }

        @Override
        public String toString() {
            return "isInList=" + isInList();
        }
    }


    @Override
    public InstrumentationState createState() {
        return new CallStack();
    }

    private void dispatch() {
        log.debug("Dispatching data loaders ({})", dataLoaderRegistry.getKeys());
        dataLoaderRegistry.dispatchAll();
    }

    private void dispatchIfNeeded(CallStack callStack) {
        if (!callStack.isInList()) {
            dispatch();
        }
    }

    @Override
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
        CallStack callStack = parameters.getInstrumentationState();
        if (callStack.isAggressivelyBatching()) {
            return dataFetcher;
        }
        //
        // currently only AsyncExecutionStrategy with DataLoader and hence this allows us to "dispatch"
        // on every object if its not using aggressive batching for other execution strategies
        // which allows them to work if used.
        return (DataFetcher<Object>) environment -> {
            Object obj = dataFetcher.get(environment);
            dispatch();
            return obj;
        };
    }

    @Override
    public InstrumentationContext<CompletableFuture<ExecutionResult>> beginDataFetchDispatch(InstrumentationDataFetchParameters parameters) {
        ExecutionStrategy queryStrategy = parameters.getExecutionContext().getQueryStrategy();
        if (!(queryStrategy instanceof AsyncExecutionStrategy)) {
            CallStack callStack = parameters.getInstrumentationState();
            callStack.setAggressivelyBatching(false);
        }
        return (result, t) -> dispatch();
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginDataFetch(InstrumentationDataFetchParameters parameters) {
        return super.beginDataFetch(parameters);
    }

    @Override
    public InstrumentationContext<Map<String, List<Field>>> beginFields(InstrumentationExecutionStrategyParameters parameters) {
        CallStack callStack = parameters.getInstrumentationState();
        return (result, t) -> dispatchIfNeeded(callStack);
    }

    /*
       When graphql-java enters a field list it re-cursively called the execution strategy again, which will cause an early flush
       to the data loader - which is not efficient from a batch point of view.  We want to allow the list of field values
       to bank up as promises and call dispatch when we are clear of a list value.

       https://github.com/graphql-java/graphql-java/issues/760
     */
    @Override
    public InstrumentationContext<CompletableFuture<ExecutionResult>> beginCompleteFieldList(InstrumentationFieldCompleteParameters parameters) {
        CallStack callStack = parameters.getInstrumentationState();
        callStack.enterList();
        return (result, t) -> {
            callStack.exitList();
            dispatchIfNeeded(callStack);
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
        Map<Object, Object> dataLoaderStats = buildStatsMap();
        statsMap.put("dataloader", dataLoaderStats);

        log.debug("Data loader stats : {}", dataLoaderStats);

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