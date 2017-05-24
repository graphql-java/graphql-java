package graphql.schema

import graphql.AssertException
import graphql.GraphQLException
import graphql.language.EnumValue
import graphql.language.StringValue
import spock.lang.Specification

import static graphql.schema.GraphQLEnumType.newEnum

class GraphQLEnumTypeTest extends Specification {

    GraphQLEnumType enumType

    def setup() {
        enumType = newEnum().name("TestEnum")
                .value("NAME", 42)
                .build();
    }

    def "parse throws exception for unknown value"() {
        when:
        enumType.getCoercing().parseValue("UNKNOWN")

        then:
        thrown(GraphQLException)
    }


    def "parse value return value for the name"() {
        expect:
        enumType.getCoercing().parseValue("NAME") == 42
    }

    def "serialize returns name for value"() {
        expect:
        enumType.getCoercing().serialize(42) == "NAME"
    }

    def "serialize throws exception for unknown value"() {
        when:
        enumType.getCoercing().serialize(12)
        then:
        thrown(GraphQLException)
    }


    def "parseLiteral return null for invalid input"() {
        expect:
        enumType.getCoercing().parseLiteral(new StringValue("foo")) == null
    }

    def "parseLiteral return null for invalid enum name"() {
        expect:
        enumType.getCoercing().parseLiteral(new EnumValue("NOT_NAME")) == null
    }

    def "parseLiteral returns value for 'NAME'"() {
        expect:
        enumType.getCoercing().parseLiteral(new EnumValue("NAME")) == 42
    }


    def "null values are not allowed"() {
        when:
        newEnum().name("AnotherTestEnum")
                .value("NAME", null)
        then:
        thrown(AssertException)
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
