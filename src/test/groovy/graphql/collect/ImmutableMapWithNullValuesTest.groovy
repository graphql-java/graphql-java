package graphql.collect

import spock.lang.Specification

class ImmutableMapWithNullValuesTest extends Specification {

    def "can have nulls as keys"() {
        when:
        def map = ImmutableMapWithNullValues.copyOf([a: null, b: "b"])
        then:
        map.get("a") == null
        map.get("b") == "b"
    }

    def "can be empty"() {
        when:
        def map = ImmutableMapWithNullValues.copyOf([:])
        then:
        map.isEmpty()
        map == ImmutableMapWithNullValues.emptyMap()

        when:
        map = ImmutableMapWithNullValues.copyOf(ImmutableKit.emptyMap())
        then:
        map.isEmpty()
        map == ImmutableMapWithNullValues.emptyMap()

        when:
        map = ImmutableMapWithNullValues.copyOf(Collections.emptyMap())
        then:
        map.isEmpty()
        map == ImmutableMapWithNullValues.emptyMap()
    }
}
