package graphql.execution.instrumentation.threadpools


import graphql.TestUtil
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetchingEnvironmentImpl
import graphql.schema.PropertyDataFetcher
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.function.Consumer

import static ExecutorInstrumentation.Action
import static java.lang.Thread.currentThread

class ExecutorInstrumentationTest extends Specification {

    private static ThreadFactory threadFactory(String name) {
        new ThreadFactory() {
            @Override
            Thread newThread(Runnable r) {
                return new Thread(r, name)
            }
        }
    }

    static class TestingObserver implements Consumer<Action> {
        def actions = []

        @Override
        void accept(Action action) {
            actions.add(action.toString() + " on " + currentThread().getName())
        }
    }

    def FetchExecutor = Executors.newSingleThreadExecutor(threadFactory("FetchThread"))
    def ProcessingExecutor = Executors.newSingleThreadExecutor(threadFactory("ProcessingThread"))

    ExecutorInstrumentation instrumentation
    def observer = new TestingObserver()


    ExecutorInstrumentation build(Executor fetchExecutor, Executor processingExecutor, Consumer<Action> observer) {
        def builder = ExecutorInstrumentation.newThreadPoolExecutionInstrumentation()
        if (fetchExecutor != null) {
            builder.fetchExecutor(fetchExecutor)
        }
        if (processingExecutor != null) {
            builder.processingExecutor(processingExecutor)
        }
        builder.actionObserver(observer).build()
    }

    DataFetchingEnvironment dfEnv(Object s) {
        DataFetchingEnvironmentImpl.newDataFetchingEnvironment().source(s).build()
    }

    CompletableFuture asCF(returnedValue) {
        (CompletableFuture) returnedValue
    }

    void setup() {
        observer = new TestingObserver()
        instrumentation = build(FetchExecutor, ProcessingExecutor, observer)
    }

    def "basic building works"() {
        expect:
        instrumentation.getFetchExecutor() == FetchExecutor
        instrumentation.getProcessingExecutor() == ProcessingExecutor
    }

    def "can handle a data fetcher that throws exceptions"() {
        when:
        DataFetcher df = { env -> throw new RuntimeException("BANG") }
        def modifiedDataFetcher = instrumentation.instrumentDataFetcher(df, null)
        def returnedValue = modifiedDataFetcher.get(null)

        then:
        returnedValue instanceof CompletableFuture

        when:
        asCF(returnedValue).join()

        then:
        def e = thrown(RuntimeException)
        e.getMessage().contains("BANG")
    }


    def "will leave trivial data fetchers as is"() {

        when:
        DataFetcher df = PropertyDataFetcher.fetching({ o -> "trivial" })
        def modifiedDataFetcher = instrumentation.instrumentDataFetcher(df, null)
        def returnedValue = modifiedDataFetcher.get(dfEnv("source"))

        then:
        modifiedDataFetcher == df
        returnedValue == "trivial"
    }


    def "will execute on another thread and transfer execution back to the processing thread"() {

        when:
        instrumentation = build(FetchExecutor, ProcessingExecutor, observer)

        DataFetcher df = { env -> currentThread().getName() }
        def modifiedDataFetcher = instrumentation.instrumentDataFetcher(df, null)
        def returnedValue = modifiedDataFetcher.get(null)

        then:
        returnedValue instanceof CompletableFuture

        when:
        def value = asCF(returnedValue).join()

        then:
        value == "FetchThread"
        observer.actions == ["FETCHING on FetchThread", "PROCESSING on ProcessingThread"]
    }

    def "will execute on another thread and stay there without a processing executor"() {

        when:
        instrumentation = build(FetchExecutor, null, observer)

        DataFetcher df = { env -> currentThread().getName() }
        def modifiedDataFetcher = instrumentation.instrumentDataFetcher(df, null)
        def returnedValue = modifiedDataFetcher.get(null)

        then:
        returnedValue instanceof CompletableFuture

        when:
        def value = asCF(returnedValue).join()

        then:
        value == "FetchThread"
        observer.actions == ["FETCHING on FetchThread", "PROCESSING on FetchThread"]
    }

    def "will fetch on current thread if the executor is null but transfer control back"() {

        when:
        def currentThreadName = currentThread().getName()
        instrumentation = build(null, ProcessingExecutor, observer)

        DataFetcher df = { env -> currentThread().getName() }
        def modifiedDataFetcher = instrumentation.instrumentDataFetcher(df, null)
        def returnedValue = modifiedDataFetcher.get(null)

        then:
        returnedValue instanceof CompletableFuture

        when:
        def value = asCF(returnedValue).join()

        then:
        value == "${currentThreadName}"
        observer.actions == ["FETCHING on ${currentThreadName}", "PROCESSING on ProcessingThread"]
    }

    def "a data fetcher can return a CF and that is handled"() {
        when:
        instrumentation = build(FetchExecutor, ProcessingExecutor, observer)

        DataFetcher df = { env -> CompletableFuture.completedFuture(currentThread().getName()) }
        def modifiedDataFetcher = instrumentation.instrumentDataFetcher(df, null)
        def returnedValue = modifiedDataFetcher.get(null)

        then:
        returnedValue instanceof CompletableFuture

        when:
        def value = asCF(returnedValue).join()

        then:
        value == "FetchThread"
        observer.actions == ["FETCHING on FetchThread", "PROCESSING on ProcessingThread"]
    }

    def "can work in a full schema"() {
        def sdl = """
            type Query { 
                field1 : String 
                field2 : String 
            }
        """
        DataFetcher df1 = { env -> CompletableFuture.completedFuture("f1" + currentThread().getName()) }
        DataFetcher df2 = { env -> "f2" + currentThread().getName() }

        def graphQL = TestUtil.graphQL(sdl, [Query: [field1: df1, field2: df2]]).instrumentation(instrumentation).build()

        when:
        def er = graphQL.execute("{field1, field2}")
        then:
        er.errors.isEmpty()
        er.data["field1"] == "f1FetchThread"
        er.data["field2"] == "f2FetchThread"
        observer.actions.sort() == [
                "FETCHING on FetchThread", "FETCHING on FetchThread",
                "PROCESSING on ProcessingThread", "PROCESSING on ProcessingThread"
        ]
    }
}
