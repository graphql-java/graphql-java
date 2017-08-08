package graphql.execution.instrumentation

class TestingInstrumentContext<T> implements InstrumentationContext<T> {
    def op
    def start = System.currentTimeMillis()
    def executionList = []

    TestingInstrumentContext(op, executionList) {
        this.op = op
        this.executionList = executionList
        executionList << "start:$op"
        println("Started $op...")
    }

    def end() {
        this.executionList << "end:$op"
        def ms = System.currentTimeMillis() - start
        println("\tEnded $op in ${ms}ms")
    }

    @Override
    void onEnd(T result, Throwable t) {
        end()
    }
}

