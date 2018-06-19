package graphql.execution.instrumentation

import java.util.concurrent.CompletableFuture

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
    void onDispatched(CompletableFuture<T> result) {
    }

    @Override
    void onCompleted(T result, Throwable t) {
        if (t) {
            throwableList.add(t)
        }
        end()
    }
}

