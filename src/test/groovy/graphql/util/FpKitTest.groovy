package graphql.util

import spock.lang.Specification

class FpKitTest extends Specification {

    class IterableThing implements Iterable {
        Iterable delegate

        @Override
        Iterator iterator() {
            return delegate.iterator()
        }
    }

    def "toCollection works as expected"() {
        def expected = ["a", "b", "c"]
        def actual

        when:
        def array = ["a", "b", "c"].toArray()
        actual = FpKit.toCollection(array)
        then:
        actual == expected

        when:
        Set set = ["a", "b", "c"].toSet()
        actual = FpKit.toCollection(set)
        then:
        actual == expected.toSet()

        when:
        List list = ["a", "b", "c"].toList()
        actual = FpKit.toCollection(list)
        then:
        actual == expected

        when:
        IterableThing iterableThing = new IterableThing(delegate: ["a", "b", "c"])
        actual = FpKit.toCollection(iterableThing)
        then:
        actual == expected
    }

    def "concat with an element"() {
        def expected = ["a", "b", "c", "d"]

        when:
        def firstList = ["a", "b", "c"]
        def element = "d"
        def actual = FpKit.concat(firstList, element)
        then:
        assert actual == expected
    }

    def "concat works as expected"() {
        def expected = ["a", "b", "c", "d"]

        when:
        def firstList = ["a", "b"]
        def secondList = ["c", "d"]
        def actual = FpKit.concat(firstList, secondList)
        then:
        assert actual == expected
    }
}
