package graphql.execution

import spock.lang.Specification

class DefaultValueUnboxerTest extends Specification {

    def "unboxValue: nested Optional for result"() {
        def expected = "a"
        def actual

        when:
        def result = Optional.of(Optional.of("a"))
        actual = DefaultValueUnboxer.unboxValue(result)
        then:
        actual == expected
    }
}