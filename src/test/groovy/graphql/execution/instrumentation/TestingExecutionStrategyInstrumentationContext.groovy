package graphql.execution.instrumentation

import graphql.ExecutionResult

class TestingExecutionStrategyInstrumentationContext extends TestingInstrumentContext<ExecutionResult> implements ExecutionStrategyInstrumentationContext {

    TestingExecutionStrategyInstrumentationContext(Object op, Object executionList, Object throwableList, Boolean useOnDispatch) {
        super(op, executionList, throwableList,useOnDispatch)
    }
}

