package graphql


import graphql.schema.GraphQLDirective
import spock.lang.Specification

class DirectivesUtilTest extends Specification {
    def d1 = GraphQLDirective.newDirective().name("d1").build()
    def d2 = GraphQLDirective.newDirective().name("d2").build()
    def d3r = GraphQLDirective.newDirective().repeatable(true).name("d3").build()
    def d4r = GraphQLDirective.newDirective().repeatable(true).name("d4").build()

    def "can filter out repeatable directives"() {
        when:
        def result = DirectivesUtil.nonRepeatableDirectivesByName([d1, d2, d3r, d3r, d4r,])
        then:
        result == [d1: d1, d2: d2]
    }

    def "can filter out repeatable directives completely"() {
        when:
        def result = DirectivesUtil.nonRepeatableDirectivesByName([d3r, d3r, d4r,])
        then:
        result == [:]
    }

    def "can create a map if all directives"() {
        when:
        def result = DirectivesUtil.allDirectivesByName([d1, d2, d3r, d3r, d4r,])
        then:
        result == [d1: [d1], d2: [d2], d3: [d3r, d3r], d4: [d4r]]
    }

    def "will assert on repeated directives"() {
        def mapOfDirectives = DirectivesUtil.allDirectivesByName([d1, d2, d3r, d3r, d4r,])
        when:
        def directive = DirectivesUtil.nonRepeatedDirectiveByNameWithAssert(mapOfDirectives, "d1")
        then:
        directive == d1

        when:
        directive = DirectivesUtil.nonRepeatedDirectiveByNameWithAssert(mapOfDirectives, "non existent")
        then:
        directive == null

        when:
        DirectivesUtil.nonRepeatedDirectiveByNameWithAssert(mapOfDirectives, "d3")
        then:
        thrown(AssertException)

        when:
        DirectivesUtil.nonRepeatedDirectiveByNameWithAssert(mapOfDirectives, "d4")
        then:
        thrown(AssertException)
    }

    def "enforcedAddAll works as expected"() {
        when: "things are empty"
        DirectivesUtil.enforceAddAll([], [d1, d2, d3r])
        then:
        notThrown(AssertException)

        when: "there is a non repeatable directive"
        DirectivesUtil.enforceAddAll([d1], [d1, d2, d3r])
        then:
        thrown(AssertException)

        when: "repeated directives are allowed to be added"
        DirectivesUtil.enforceAddAll([d3r], [d1, d2, d3r])
        then:
        notThrown(AssertException)
    }

    def "enforcedAdd works as expected"() {
        when: "things are empty"
        DirectivesUtil.enforceAdd([], d3r)
        then:
        notThrown(AssertException)

        when: "there is a non repeatable directive"
        DirectivesUtil.enforceAdd([d1], d1)
        then:
        thrown(AssertException)

        when: "repeated directives are allowed to be added"
        DirectivesUtil.enforceAdd([d3r], d3r)
        then:
        notThrown(AssertException)
    }
}
