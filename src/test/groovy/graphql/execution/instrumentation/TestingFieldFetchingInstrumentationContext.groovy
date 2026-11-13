package graphql.execution.instrumentation

import graphql.execution.DataFetcherResult

class TestingFieldFetchingInstrumentationContext extends TestingInstrumentContext<Object> implements FieldFetchingInstrumentationContext {

    TestingFieldFetchingInstrumentationContext(Object op, Object executionList, Object throwableList, Boolean useOnDispatch) {
        super(op, executionList, throwableList, useOnDispatch)
    }

    @Override
    void onExceptionHandled(DataFetcherResult<Object> dataFetcherResult) {
        executionList << "onExceptionHandled:$op"
    }
}

