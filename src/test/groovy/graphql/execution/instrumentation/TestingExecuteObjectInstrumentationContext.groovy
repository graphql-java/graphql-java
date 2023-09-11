package graphql.execution.instrumentation

class TestingExecuteObjectInstrumentationContext extends TestingInstrumentContext<Map<String, Object>> implements ExecuteObjectInstrumentationContext {

    TestingExecuteObjectInstrumentationContext(Object op, Object executionList, Object throwableList, Boolean useOnDispatch) {
        super(op, executionList, throwableList, useOnDispatch)
    }
}

