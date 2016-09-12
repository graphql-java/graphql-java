package graphql.schema

import spock.lang.Specification

import static graphql.Scalars.GraphQLString

class MapDataFetcherTest extends Specification {

    def environment = new DataFetchingEnvironment(["key":"value"], null, null, null, GraphQLString, null, null)

    def "get defined value"() {
        given:
        def fetcher = new MapDataFetcher("key")

        when:
        def result = fetcher.get(environment)

        then:
        result == "value"
    }

    def "get undefinfed value"() {
        given:
        def fetcher = new MapDataFetcher("notakey")

        when:
        def result = fetcher.get(environment)

        then:
        result == null
    }
}
