package graphql.schema

import spock.lang.Specification

import static graphql.Scalars.GraphQLString


class StaticDataFetcherTest extends Specification {

    def "get value"() {
        given:
        def environment = new DataFetchingEnvironment(null, null, null, null, GraphQLString, null, null)
        when:
        def result = new StaticDataFetcher("value").get(environment)
        then:
        result == "value"
    }
}
