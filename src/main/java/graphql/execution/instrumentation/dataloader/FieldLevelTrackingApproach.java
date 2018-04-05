package graphql.execution.instrumentation.dataloader;

import graphql.ExecutionResult;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationDeferredFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import org.dataloader.DataLoaderRegistry;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

import static graphql.execution.instrumentation.SimpleInstrumentationContext.whenDispatched;

/**
 * This approach uses field level tracking to achieve its aims of making the data loader more efficient
 */
public class FieldLevelTrackingApproach {
    private final DataLoaderRegistry dataLoaderRegistry;
    private final Logger log;

    private static class CallStack extends DataLoaderDispatcherInstrumentationState {

        private final Map<Integer, Integer> outstandingFieldFetchCounts = new HashMap<>();

        private void setOutstandingFieldFetches(int level, int count) {
            synchronized (this) {
                outstandingFieldFetchCounts.put(level, count);
            }
        }

        private int decrementOutstandingFieldFetches(int level) {
            int newCount;
            synchronized (this) {
                newCount = outstandingFieldFetchCounts.getOrDefault(level, 1) - 1;
                outstandingFieldFetchCounts.put(level, newCount);
            }
            return newCount;
        }

        private int getOutstandingFieldFetches(int level) {
            return outstandingFieldFetchCounts.getOrDefault(level, 0);
        }

        private boolean thereAreOutstandingFieldFetches(int startLevel) {
            synchronized (this) {
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
    }

    public FieldLevelTrackingApproach(Logger log, DataLoaderRegistry dataLoaderRegistry) {
        this.dataLoaderRegistry = dataLoaderRegistry;
        this.log = log;
    }

    public InstrumentationState createState() {
        return new CallStack();
    }

    InstrumentationContext<ExecutionResult> beginExecuteOperation() {
        return whenDispatched((result) -> dispatch());
    }

    InstrumentationContext<ExecutionResult> beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        CallStack callStack = parameters.getInstrumentationState();
        // +1 because here we are before any field dispatching
        int targetLevel = parameters.getExecutionStrategyParameters().getPath().getLevel() + 1;
        int fieldCount = parameters.getExecutionStrategyParameters().getFields().size();
        callStack.setOutstandingFieldFetches(targetLevel, fieldCount);
        return whenDispatched((result) -> dispatchIfNeeded(callStack, parameters.getExecutionStrategyParameters()));
    }

    InstrumentationContext<ExecutionResult> beginDeferredField(InstrumentationDeferredFieldParameters parameters) {
        CallStack callStack = parameters.getInstrumentationState();
        int targetLevel = parameters.getTypeInfo().getPath().getLevel();
        int fieldCount = 1;
        callStack.setOutstandingFieldFetches(targetLevel, fieldCount);
        // with deferred fields we aggressively dispatch the data loaders because the neat hierarchy of
        // outstanding fields and so on is lost because the field has jumped out of the execution tree
        return whenDispatched((result) -> dispatchIfNeeded(callStack, parameters.getExecutionStrategyParameters()));
    }

    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        CallStack callStack = parameters.getInstrumentationState();
        int level = parameters.getEnvironment().getFieldTypeInfo().getPath().getLevel();
        callStack.decrementOutstandingFieldFetches(level);
        return new SimpleInstrumentationContext<>();
    }

    private void dispatchIfNeeded(CallStack callStack, ExecutionStrategyParameters executionStrategyParameters) {
        int level = executionStrategyParameters.getPath().getLevel();
        boolean outstandingDispatches = callStack.thereAreOutstandingFieldFetches(level);
        boolean endOfListOnAllLevels = isEndOfListOnAllLevels(executionStrategyParameters);
        if (endOfListOnAllLevels && !outstandingDispatches) {
            dispatch();
        }
    }

    void dispatch() {
        log.debug("Dispatching data loaders ({})", dataLoaderRegistry.getKeys());
        dataLoaderRegistry.dispatchAll();
    }

    @SuppressWarnings("SimplifiableIfStatement")
    private boolean isEndOfListImpl(ExecutionStrategyParameters executionStrategyParameters) {
        if (executionStrategyParameters == null) {
            return true;
        }
        int listSize = executionStrategyParameters.getListSize();
        if (listSize == 0) {
            return true;
        }
        int index = executionStrategyParameters.getCurrentListIndex() + 1;
        return index == listSize;
    }

    private boolean isEndOfListOnAllLevels(ExecutionStrategyParameters executionStrategyParameters) {
        boolean endOfList = isEndOfListImpl(executionStrategyParameters);
        ExecutionStrategyParameters parent = executionStrategyParameters.getParent();
        return endOfList &&
                (parent == null || isEndOfListOnAllLevels(parent));
    }
}
