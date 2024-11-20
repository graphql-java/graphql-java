package graphql.schema

import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.function.BiFunction

class DataFetcherFactoriesTest extends Specification {

    def cfDF = new StaticDataFetcher(CompletableFuture.completedFuture("hello"))
    def pojoDF = new StaticDataFetcher("goodbye")

    def "delegation happens as expected"() {
        given:

        def mapper = new BiFunction<DataFetchingEnvironment, Object, Object>() {
            @Override
            Object apply(DataFetchingEnvironment dataFetchingEnvironment, Object o) {
                return String.valueOf(o) + " world"
            }
        }
        when:
        def cfWrappedDF = DataFetcherFactories.wrapDataFetcher(cfDF, mapper)
        def pojoWrappedDF = DataFetcherFactories.wrapDataFetcher(pojoDF, mapper)

        then:
        (cfWrappedDF.get(null) as CompletableFuture).join() == "hello world"
        pojoWrappedDF.get(null) == "goodbye world"
    }

    def "will use given df"() {
        def fetcherFactory = DataFetcherFactories.useDataFetcher(pojoDF)

        when:
        def value = fetcherFactory.get(null).get(null)

        then:
        value == "goodbye"
    }
}
