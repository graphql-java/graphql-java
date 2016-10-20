package graphql.schema

import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLString

class PropertyDataFetcherTest extends Specification {

    class Source {
        private String field = "value"
        public String getProperty() { return field }
    }

    class SourceBooleanIs {
        private Boolean field = true
        public Boolean isProperty() { return field }
    }

    class SourceBooleanGet {
        private Boolean field = true
        public Boolean getProperty() { return field }
    }

    def "get non-boolean property value"() {
        given:
        def environment = new DataFetchingEnvironment(new Source(), null, null, null, GraphQLString, null, null)
        def fetcher = new PropertyDataFetcher("property", GraphQLString)

        when:
        def result = fetcher.get(environment)

        then:
        result == "value"
    }

    def "get boolean property value from source with is-getter"() {
        given:
        def environment = new DataFetchingEnvironment(new SourceBooleanIs(), null, null, null, GraphQLBoolean, null, null)
        def fetcher = new PropertyDataFetcher("property", GraphQLBoolean)

        when:
        def result = fetcher.get(environment)

        then:
        result == true
    }

    def "get boolean property value from source with get-getter"() {
        given:
        def environment = new DataFetchingEnvironment(new SourceBooleanGet(), null, null, null, GraphQLBoolean, null, null)
        def fetcher = new PropertyDataFetcher("property", GraphQLBoolean)

        when:
        def result = fetcher.get(environment)

        then:
        result == true
    }

    def "get a nonexistent property"() {
        given:
        def environment = new DataFetchingEnvironment(new Source(), null, null, null, GraphQLString, null, null)
        def fetcher = new PropertyDataFetcher("notaproperty", GraphQLString)

        when:
        def result = fetcher.get(environment)

        then:
        result == null
    }
}
