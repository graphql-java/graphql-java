package graphql.schema

import graphql.AssertException
import graphql.GraphQLContext
import graphql.language.EnumValue
import graphql.language.StringValue
import spock.lang.Specification

import static graphql.schema.GraphQLEnumType.newEnum

class GraphQLEnumTypeTest extends Specification {

    GraphQLEnumType enumType

    def setup() {
        enumType = newEnum().name("TestEnum")
                .value("NAME", 42)
                .build()
    }

    def "parse throws exception for unknown value"() {
        when:
        enumType.parseValue("UNKNOWN", GraphQLContext.default, Locale.default)

        then:
        thrown(CoercingParseValueException)
    }

    def "parse throws exception for unknown value with deprecated method"() {
        when:
        enumType.parseValue("UNKNOWN") // Retain for test coverage

        then:
        thrown(CoercingParseValueException)
    }

    def "parse value return value for the name"() {
        expect:
        enumType.parseValue("NAME", GraphQLContext.default, Locale.default) == 42
    }

    def "serialize returns name for value"() {
        expect:
        enumType.serialize(42, GraphQLContext.default, Locale.default) == "NAME"
    }

    def "serialize returns name for value with deprecated method"() {
        expect:
        enumType.serialize(42) == "NAME"
    }

    def "serialize throws exception for unknown value"() {
        when:
        enumType.serialize(12, GraphQLContext.default, Locale.default)
        then:
        thrown(CoercingSerializeException)
    }

    def "parseLiteral return null for invalid input"() {
        when:
        enumType.parseLiteral(StringValue.newStringValue("foo").build(),
                GraphQLContext.default,
                Locale.default)
        then:
        thrown(CoercingParseLiteralException)
    }

    def "parseLiteral return null for invalid input with deprecated method"() {
        when:
        enumType.parseLiteral(StringValue.newStringValue("foo").build())
        then:
        thrown(CoercingParseLiteralException)
    }

    def "parseLiteral return null for invalid enum name"() {
        when:
        enumType.parseLiteral(EnumValue.newEnumValue("NOT_NAME").build(),
                GraphQLContext.default,
                Locale.default)
        then:
        thrown(CoercingParseLiteralException)
    }

    def "parseLiteral returns value for 'NAME'"() {
        expect:
        enumType.parseLiteral(EnumValue.newEnumValue("NAME").build(),
                GraphQLContext.default,
                Locale.default) == 42
    }

    def "null values are not allowed"() {
        when:
        newEnum().name("AnotherTestEnum")
                .value("NAME", null)
        then:
        thrown(AssertException)
    }

    def "duplicate value definition overwrites"() {
        when:
        def enumType = newEnum().name("AnotherTestEnum")
                .value("NAME", 42)
                .value("NAME", 43)
                .build()
        then:
        enumType.getValue("NAME").getValue() == 43
    }

    enum Episode {
        NEWHOPE, EMPIRE
    }

    def "serialize Java enum objects with String definition values"() {

        given:
        enumType = newEnum().name("Episode")
                .value("NEWHOPE", "NEWHOPE")
                .value("EMPIRE", "EMPIRE")
                .build()

        when:
        def serialized = enumType.serialize(Episode.EMPIRE, GraphQLContext.default, Locale.default)

        then:
        serialized == "EMPIRE"
    }

    def "serialize Java enum objects with Java enum definition values"() {

        given:
        enumType = newEnum().name("Episode")
                .value("NEWHOPE", Episode.NEWHOPE)
                .value("EMPIRE", Episode.EMPIRE)
                .build()

        when:
        def serialized = enumType.serialize(Episode.NEWHOPE, GraphQLContext.default, Locale.default)

        then:
        serialized == "NEWHOPE"
    }

    def "serialize String objects with Java enum definition values"() {

        given:
        enumType = newEnum().name("Episode")
                .value("NEWHOPE", Episode.NEWHOPE)
                .value("EMPIRE", Episode.EMPIRE)
                .build()

        String stringInput = Episode.NEWHOPE.toString()

        when:
        def serialized = enumType.serialize(stringInput, GraphQLContext.default, Locale.default)

        then:
        serialized == "NEWHOPE"
    }

    def "object can be transformed"() {
        given:
        def startEnum = newEnum().name("E1")
                .description("E1_description")
                .value("A")
                .value("B")
                .value("C")
                .value("D")
                .build()
        when:
        def transformedEnum = startEnum.transform({
            it
                    .name("E2")
                    .clearValues()
                    .value("X", 1)
                    .value("Y", 2)
                    .value("Z", 3)

        })

        then:
        startEnum.name == "E1"
        startEnum.description == "E1_description"
        startEnum.getValues().size() == 4
        startEnum.getValue("A").value == "A"
        startEnum.getValue("B").value == "B"
        startEnum.getValue("C").value == "C"
        startEnum.getValue("D").value == "D"

        transformedEnum.name == "E2"
        transformedEnum.description == "E1_description" // left alone
        transformedEnum.getValues().size() == 3
        transformedEnum.getValue("X").value == 1
        transformedEnum.getValue("Y").value == 2
        transformedEnum.getValue("Z").value == 3

    }
}
