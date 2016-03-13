package graphql.schema

import spock.lang.Specification

import graphql.AssertException

import static graphql.schema.GraphQLEnumType.newEnum


class GraphQLEnumTypeTest extends Specification {

    GraphQLEnumType enumType

    def setup() {
        enumType = newEnum().name("TestEnum")
                .value("NAME", 42)
                .build();
    }

    def "parse value returns null for unknown value"() {
        expect:
        enumType.getCoercing().parseValue("UNKNOWN") == null
    }


    def "parse value return value for the name"() {
        expect:
        enumType.getCoercing().parseValue("NAME") == 42
    }

    def "serialize returns name for value"() {
        expect:
        enumType.getCoercing().serialize(42) == "NAME"
    }

    def "serialize returns null for unknown value"() {
        expect:
        enumType.getCoercing().serialize(12) == null
    }

    def "duplicate value definition fails"() {
        when:
        newEnum().name("AnotherTestEnum")
                .value("NAME", 42)
                .value("NAME", 43)
                .build();
        then:
        thrown(AssertException)
    }
}
