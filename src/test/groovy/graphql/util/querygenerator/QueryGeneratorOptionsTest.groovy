package graphql.util.querygenerator

import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldsContainer
import spock.lang.Specification

import java.util.function.Predicate

class QueryGeneratorOptionsTest extends Specification {

    def "default builder sets maxFieldCount to 10_000 and always-true predicates"() {
        when:
        def options = QueryGeneratorOptions.newBuilder().build()

        then:
        options.maxFieldCount == 10_000
        options.filterFieldContainerPredicate.test(Mock(GraphQLFieldsContainer))
        options.filterFieldDefinitionPredicate.test(Mock(GraphQLFieldDefinition))
    }

    def "builder sets maxFieldCount to custom value within range"() {
        when:
        def options = QueryGeneratorOptions.newBuilder()
                .maxFieldCount(500)
                .build()

        then:
        options.maxFieldCount == 500
    }

    def "builder throws exception if maxFieldCount is negative"() {
        when:
        QueryGeneratorOptions.newBuilder().maxFieldCount(-1)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Max field count cannot be negative"
    }

    def "builder throws exception if maxFieldCount exceeds MAX_FIELD_COUNT_LIMIT"() {
        when:
        QueryGeneratorOptions.newBuilder().maxFieldCount(10_001)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Max field count cannot exceed 10000"
    }

    def "builder uses custom field container predicate"() {
        given:
        def customPredicate = Mock(Predicate)
        customPredicate.test(_) >> false

        when:
        def options = QueryGeneratorOptions.newBuilder()
                .filterFieldContainerPredicate(customPredicate)
                .build()

        then:
        !options.filterFieldContainerPredicate.test(Mock(GraphQLFieldsContainer))
    }

    def "builder uses custom field definition predicate"() {
        given:
        def customPredicate = { GraphQLFieldDefinition defn -> defn.name == "includeMe" } as Predicate

        and:
        def included = Mock(GraphQLFieldDefinition) { getName() >> "includeMe" }
        def excluded = Mock(GraphQLFieldDefinition) { getName() >> "skipMe" }

        when:
        def options = QueryGeneratorOptions.newBuilder()
                .filterFieldDefinitionPredicate(customPredicate)
                .build()

        then:
        options.filterFieldDefinitionPredicate.test(included)
        !options.filterFieldDefinitionPredicate.test(excluded)
    }
}
