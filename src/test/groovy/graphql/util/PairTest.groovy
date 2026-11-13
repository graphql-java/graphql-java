package graphql.util

import spock.lang.Specification

class PairTest extends Specification {
    def "constructor initializes fields correctly"() {
        when:
        def pair = new Pair<>("hello", 123)

        then:
        pair.first == "hello"
        pair.second == 123
    }

    def "static pair method creates Pair instance"() {
        when:
        def pair = Pair.pair("foo", "bar")

        then:
        pair instanceof Pair
        pair.first == "foo"
        pair.second == "bar"
    }

    def "toString returns formatted string"() {
        expect:
        new Pair<>(1, 2).toString() == "(1, 2)"
        Pair.pair("a", "b").toString() == "(a, b)"
        new Pair<>(null, null).toString() == "(null, null)"
    }
}
