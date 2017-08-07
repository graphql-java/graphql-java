package graphql.execution.instrumentation

class TestingInstrumentContext<T> implements InstrumentationContext<T> {
    def op
    def start = System.currentTimeMillis()
    def executionList = []
    def throwableList = []

    TestingInstrumentContext(op, executionList, throwableList) {
        this.op = op
        this.executionList = executionList
        this.throwableList = throwableList
        executionList << "start:$op"
        println("Started $op...")
    }

    def end() {
        this.executionList << "end:$op"
        def ms = System.currentTimeMillis() - start
        println("\tEnded $op in ${ms}ms")
    }

    @Override
    void onEnd(T result) {
        end()
    }

    @Override
    void onEnd(Throwable t) {
        throwableList.add(t)
        end()
    }
}

