package graphql.schema

import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class AsyncDataFetcherTest extends Specification {

    DataFetcher wrappedDataFetcher = { env -> "value" }

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
