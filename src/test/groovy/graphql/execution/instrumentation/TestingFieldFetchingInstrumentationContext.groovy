package graphql.execution.instrumentation

class TestingFieldFetchingInstrumentationContext extends TestingInstrumentContext<Map<String, Object>> implements FieldFetchingInstrumentationContext {

    TestingFieldFetchingInstrumentationContext(Object op, Object executionList, Object throwableList, Boolean useOnDispatch) {
        super(op, executionList, throwableList, useOnDispatch)
    }
}

