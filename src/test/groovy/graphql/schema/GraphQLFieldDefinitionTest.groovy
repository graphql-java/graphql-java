package graphql.schema

import graphql.AssertException
import spock.lang.Specification

class GraphQLFieldDefinitionTest extends Specification {

    def "dataFetcher can't be null"() {
        when:
        GraphQLFieldDefinition.newFieldDefinition().dataFetcher(null)
        then:
        def exception = thrown(AssertException)
        exception.getMessage().contains("dataFetcher")
    }
}
