package graphql.execution

import spock.lang.Specification

class DefaultResponseMapFactoryTest extends Specification {

    def "no keys"() {
        given:
        var sut = new DefaultResponseMapFactory()

        when:
        var result = sut.createInsertionOrdered(List.of(), List.of())

        then:
        result.isEmpty()
        result instanceof LinkedHashMap
    }

    def "1 key"() {
        given:
        var sut = new DefaultResponseMapFactory()

        when:
        var result = sut.createInsertionOrdered(List.of("name"), List.of("Mario"))

        then:
        result == ["name": "Mario"]
        result instanceof LinkedHashMap
    }

    def "2 keys"() {
        given:
        var sut = new DefaultResponseMapFactory()

        when:
        var result = sut.createInsertionOrdered(List.of("name", "age"), List.of("Mario", 18))

        then:
        result == ["name": "Mario", "age": 18]
        result instanceof LinkedHashMap
    }
}
