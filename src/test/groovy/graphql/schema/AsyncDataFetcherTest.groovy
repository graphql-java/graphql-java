package graphql.schema

import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class AsyncDataFetcherTest extends Specification {

    DataFetcher wrappedDataFetcher = { env -> "value" }

    def "nullable resolver results can complete asynchronously"() {
        given:
        DataFetcher<String> fetcher = { env -> null }
        def environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment().build()
        Executor directExecutor = { command -> command.run() }

        expect:
        AsyncDataFetcher.async(fetcher).get(environment).join() == null
        AsyncDataFetcher.async(fetcher, directExecutor).get(environment).join() == null
        new AsyncDataFetcher<>(fetcher, directExecutor).get(environment).join() == null
    }

    def "A data fetcher can be made asynchronous with AsynchronousDataFetcher#async"() {
        given:
        DataFetchingEnvironment environment = Mock(DataFetchingEnvironment)

        when:
        DataFetcher asyncDataFetcher = AsyncDataFetcher.async(wrappedDataFetcher)

        then:
        asyncDataFetcher.get(environment) instanceof CompletableFuture
        asyncDataFetcher.get(environment).get() == "value"
    }

    def "will accept its own executor"() {
        given:
        DataFetchingEnvironment environment = Mock(DataFetchingEnvironment)

        when:
        Executor executor = Executors.newSingleThreadExecutor()
        DataFetcher asyncDataFetcher = AsyncDataFetcher.async(wrappedDataFetcher, executor)

        then:
        asyncDataFetcher.get(environment) instanceof CompletableFuture
        asyncDataFetcher.get(environment).get() == "value"
    }
}
