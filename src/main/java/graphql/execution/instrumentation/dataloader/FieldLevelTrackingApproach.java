package graphql.execution.instrumentation.dataloader;

import graphql.ExecutionResult;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import org.dataloader.DataLoaderRegistry;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static graphql.execution.instrumentation.SimpleInstrumentationContext.whenDispatched;

/**
 * This approach uses field level tracking to achieve its aims of making the data loader more efficient
 */
public class FieldLevelTrackingApproach {
    private final DataLoaderRegistry dataLoaderRegistry;
    private final Logger log;

    private static class CallStack extends DataLoaderDispatcherInstrumentationState {
        private final Map<Integer, Integer> outstandingFieldFetchCounts = new ConcurrentHashMap<>();

        private void setOutstandingFieldFetches(int level, int count) {
            outstandingFieldFetchCounts.put(level, count);
        }

        // TODO: should be threadsafe
        private int decrementOutstandingFieldFetches(int level) {
            int newCount = outstandingFieldFetchCounts.getOrDefault(level, 1) - 1;
            outstandingFieldFetchCounts.put(level, newCount);
            return newCount;
        }

        private int getOutstandingFieldFetches(int level) {
            return outstandingFieldFetchCounts.getOrDefault(level, 0);
        }

        private boolean thereAreOutstandingFieldFetches(int startLevel) {
            while (startLevel >= 0) {
                int count = getOutstandingFieldFetches(startLevel);
                if (count > 0) {
                    return true;
                }
                startLevel--;
            }
            return false;
        }
    }

    public InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters) {
        return whenDispatched((result) -> dispatch());
    }

    public InstrumentationContext<ExecutionResult> beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        CallStack callStack = parameters.getInstrumentationState();
        int level = parameters.getExecutionStrategyParameters().getPath().getLevel() + 1; // +1 because we are looking forward
        int fieldCount = parameters.getExecutionStrategyParameters().getFields().size();
        callStack.setOutstandingFieldFetches(level, fieldCount);
        return whenDispatched((result) -> dispatchIfNeeded(callStack, parameters.getExecutionStrategyParameters()));
    }

    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        CallStack callStack = parameters.getInstrumentationState();
        int level = parameters.getEnvironment().getFieldTypeInfo().getPath().getLevel();
        callStack.decrementOutstandingFieldFetches(level);
        return new SimpleInstrumentationContext<>();
    }

    public InstrumentationState createState() {
        return new CallStack();
    }


    public FieldLevelTrackingApproach(Logger log, DataLoaderRegistry dataLoaderRegistry) {
        this.dataLoaderRegistry = dataLoaderRegistry;
        this.log = log;
    }

    void dispatch() {
        log.debug("Dispatching data loaders ({})", dataLoaderRegistry.getKeys());

        List<String> dlKeysWithData = dataLoaderRegistry.getKeys().stream().filter(key -> dataLoaderRegistry.getDataLoader(key).dispatchDepth() > 0).collect(Collectors.toList());
        if (!dlKeysWithData.isEmpty()) {
            System.out.println("\t\tDispatched!");
        }
        dlKeysWithData.forEach(key -> {
            System.out.println(String.format("'%s' has %d depth", key, dataLoaderRegistry.getDataLoader(key).dispatchDepth()));
        });

        System.out.println("\n\n");
        dataLoaderRegistry.dispatchAll();
    }

    private boolean isEndOfListImpl(ExecutionStrategyParameters executionStrategyParameters) {
        if (executionStrategyParameters == null) {
            return true;
        }
        if (executionStrategyParameters.getListSize() == 0) {
            return true;
        }
        return executionStrategyParameters.getCurrentListIndex() + 1 == executionStrategyParameters.getListSize();
    }

    private boolean isEndOfListOnAllLevels(ExecutionStrategyParameters executionStrategyParameters) {
        return isEndOfListImpl(executionStrategyParameters) &&
                (executionStrategyParameters.getParent() == null || isEndOfListOnAllLevels(executionStrategyParameters.getParent()));
    }

    private void dispatchIfNeeded(CallStack callStack, ExecutionStrategyParameters executionStrategyParameters) {
        int level = executionStrategyParameters.getPath().getLevel();
        boolean outstandingDispatches = callStack.thereAreOutstandingFieldFetches(level);
        if (isEndOfListOnAllLevels(executionStrategyParameters) && !outstandingDispatches) {
            dispatch();
        }
    }

}
