package graphql.execution.instrumentation.dataloader;

import graphql.ExecutionResult;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionPath;
import graphql.execution.FieldValueInfo;
import graphql.execution.MergedField;
import graphql.execution.instrumentation.DeferredFieldInstrumentationContext;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationDeferredFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import org.dataloader.DataLoaderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class AbstractTrackingApproach implements TrackingApproach {

    private static final Logger log = LoggerFactory.getLogger(AbstractTrackingApproach.class);

    private final DataLoaderRegistry dataLoaderRegistry;

    private final RequestStack stack = new RequestStack();

    public AbstractTrackingApproach(DataLoaderRegistry dataLoaderRegistry) {
        this.dataLoaderRegistry = dataLoaderRegistry;
    }

    protected RequestStack getStack() {
        return stack;
    }

    public ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        ExecutionId executionId = parameters.getExecutionContext().getExecutionId();
        ExecutionPath path = parameters.getExecutionStrategyParameters().getPath();
        int parentLevel = path.getLevel();
        int curLevel = parentLevel + 1;
        int fieldCount = parameters.getExecutionStrategyParameters().getFields().size();
        synchronized (stack) {
            stack.increaseExpectedFetchCount(executionId, curLevel, fieldCount);
            stack.increaseHappenedStrategyCalls(executionId, curLevel);
        }

        return new ExecutionStrategyInstrumentationContext() {
            @Override
            public void onDispatched(CompletableFuture<ExecutionResult> result) {

            }

            @Override
            public void onCompleted(ExecutionResult result, Throwable t) {

            }

            @Override
            public void onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) {
                synchronized (stack) {
                    stack.setStatus(executionId, handleOnFieldValuesInfo(fieldValueInfoList, stack, executionId, curLevel));
                    if (stack.allReady()) {
                        dispatchWithoutLocking();
                    }
                }
            }

            @Override
            public void onDeferredField(MergedField field) {
                // fake fetch count for this field
                synchronized (stack) {
                    stack.increaseFetchCount(executionId, curLevel);
                    stack.setStatus(executionId, dispatchIfNeeded(stack, executionId, curLevel));
                    if (stack.allReady()) {
                        dispatchWithoutLocking();
                    }
                }
            }
        };
    }

    //
    // thread safety : called with synchronised(callStack)
    //
    private boolean handleOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, RequestStack callStack, ExecutionId executionId, int curLevel) {
        callStack.increaseHappenedOnFieldValueCalls(executionId, curLevel);
        int expectedStrategyCalls = 0;
        for (FieldValueInfo fieldValueInfo : fieldValueInfoList) {
            if (fieldValueInfo.getCompleteValueType() == FieldValueInfo.CompleteValueType.OBJECT) {
                expectedStrategyCalls++;
            } else if (fieldValueInfo.getCompleteValueType() == FieldValueInfo.CompleteValueType.LIST) {
                expectedStrategyCalls += getCountForList(fieldValueInfo);
            }
        }
        callStack.increaseExpectedStrategyCalls(executionId, curLevel + 1, expectedStrategyCalls);
        return dispatchIfNeeded(callStack, executionId, curLevel + 1);
    }

    private int getCountForList(FieldValueInfo fieldValueInfo) {
        int result = 0;
        for (FieldValueInfo cvi : fieldValueInfo.getFieldValueInfos()) {
            if (cvi.getCompleteValueType() == FieldValueInfo.CompleteValueType.OBJECT) {
                result++;
            } else if (cvi.getCompleteValueType() == FieldValueInfo.CompleteValueType.LIST) {
                result += getCountForList(cvi);
            }
        }
        return result;
    }

    public DeferredFieldInstrumentationContext beginDeferredField(InstrumentationDeferredFieldParameters parameters) {
        ExecutionId executionId = parameters.getExecutionContext().getExecutionId();
        int level = parameters.getExecutionStrategyParameters().getPath().getLevel();
        synchronized (stack) {
            stack.clearAndMarkCurrentLevelAsReady(executionId, level);
        }

        return new DeferredFieldInstrumentationContext() {
            @Override
            public void onDispatched(CompletableFuture<ExecutionResult> result) {

            }

            @Override
            public void onCompleted(ExecutionResult result, Throwable t) {
            }

            @Override
            public void onFieldValueInfo(FieldValueInfo fieldValueInfo) {
                synchronized (stack) {
                    stack.setStatus(executionId, handleOnFieldValuesInfo(Collections.singletonList(fieldValueInfo), stack, executionId, level));
                    if (stack.allReady()) {
                        dispatchWithoutLocking();
                    }
                }
            }
        };
    }

    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        ExecutionId executionId = parameters.getExecutionContext().getExecutionId();
        ExecutionPath path = parameters.getEnvironment().getExecutionStepInfo().getPath();
        int level = path.getLevel();
        return new InstrumentationContext<Object>() {

            @Override
            public void onDispatched(CompletableFuture result) {
                synchronized (stack) {
                    stack.increaseFetchCount(executionId, level);
                    stack.setStatus(executionId, dispatchIfNeeded(stack, executionId, level));

                    if (stack.allReady()) {
                        dispatchWithoutLocking();
                    }
                }
            }

            @Override
            public void onCompleted(Object result, Throwable t) {
            }
        };
    }

    @Override
    public void removeTracking(ExecutionId executionId) {
        synchronized (stack) {
            stack.removeExecution(executionId);
        }
    }


    //
    // thread safety : called with synchronised(callStack)
    //
    private boolean dispatchIfNeeded(RequestStack callStack, ExecutionId executionId, int level) {
        if (levelReady(callStack, executionId, level)) {
            return callStack.dispatchIfNotDispatchedBefore(executionId, level);
        }
        return false;
    }

    //
    // thread safety : called with synchronised(callStack)
    //
    private boolean levelReady(RequestStack callStack, ExecutionId executionId, int level) {
        if (level == 1) {
            // level 1 is special: there is only one strategy call and that's it
            return callStack.allFetchesHappened(executionId, 1);
        }
        return (levelReady(callStack, executionId, level - 1) && callStack.allOnFieldCallsHappened(executionId, level - 1)
            && callStack.allStrategyCallsHappened(executionId, level) && callStack.allFetchesHappened(executionId, level));
    }

    public void dispatch() {
        synchronized (stack) {
            dispatchWithoutLocking();
        }
    }

    private void dispatchWithoutLocking() {
        log.debug("Dispatching data loaders ({})", dataLoaderRegistry.getKeys());
        dataLoaderRegistry.dispatchAll();
        stack.allReset();
    }
}
