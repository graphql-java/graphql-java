package graphql.execution.instrumentation

class TestingFieldFetchingInstrumentationContext extends TestingInstrumentContext<Object> implements FieldFetchingInstrumentationContext {

    TestingFieldFetchingInstrumentationContext(Object op, Object executionList, Object throwableList, Boolean useOnDispatch) {
        super(op, executionList, throwableList, useOnDispatch)
    }
}

