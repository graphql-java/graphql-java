package graphql.schema

import spock.lang.Specification

import static graphql.Scalars.GraphQLString

class FieldDataFetcherTest extends Specification {

    class Source {
        public String field = "value"
    }

    def "get defined field value"() {
        given:
        def environment = new DataFetchingEnvironment(new Source(), null, null, null, GraphQLString, null, null)

        when:
        def result = new FieldDataFetcher("field").get(environment)

        then:
        result == "value"
    }

    def "get undefined field value"() {
        given:
        def environment = new DataFetchingEnvironment(new Source(), null, null, null, GraphQLString, null, null)

        when:
        def result = new FieldDataFetcher("notafield").get(environment)

        then:
        result == null
    }
}
