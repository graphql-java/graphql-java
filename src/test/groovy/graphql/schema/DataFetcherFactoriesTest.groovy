package graphql.schema

import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.function.BiFunction

class DataFetcherFactoriesTest extends Specification {

    def cfDF = new StaticDataFetcher(CompletableFuture.completedFuture("hello"))
    def pojoDF = new StaticDataFetcher("goodbye")

    def "maps nullable resolver values and permits nullable results - async #async mapped #mapped"() {
        given:
        DataFetcher<?> fetcher = { environment -> async ? CompletableFuture.completedFuture(null) : null }
        def wrapped = DataFetcherFactories.wrapDataFetcher(fetcher, { environment, value ->
            assert value == null
            mapped
        })

        when:
        def result = wrapped.get(DataFetchingEnvironmentImpl.newDataFetchingEnvironment().build())

        then:
        (async ? result.join() : result) == mapped

        where:
        async | mapped
        false | null
        true  | null
        false | 'mapped'
        true  | 'mapped'
    }

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
        def value = fetcherFactory.get((GraphQLFieldDefinition)null).get(null)

        then:
        value == "goodbye"
    }

    def "will use given df via field"() {
        def fetcherFactory = DataFetcherFactories.useDataFetcher(pojoDF)

        when:
        def value = fetcherFactory.get((GraphQLFieldDefinition) null).get(null)

        then:
        value == "goodbye"
    }
}
