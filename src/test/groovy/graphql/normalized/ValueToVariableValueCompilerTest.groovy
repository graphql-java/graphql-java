package graphql.normalized

import graphql.language.ArrayValue
import graphql.language.BooleanValue
import graphql.language.EnumValue
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.NullValue
import graphql.language.ObjectField
import graphql.language.ObjectValue
import graphql.language.StringValue
import graphql.language.Type
import graphql.language.TypeName
import graphql.schema.idl.TypeUtil
import spock.lang.Specification

class ValueToVariableValueCompilerTest extends Specification {

    def "cam handle different ast Value objects"() {

        expect:
        def actual = ValueToVariableValueCompiler.normalisedValueToVariableValue(value)
        actual == expected

        where:
        value                     | expected
        NullValue.of()            | null
        IntValue.of(666)          | 666
        StringValue.of("str")     | "str"
        BooleanValue.of(true)     | true
        FloatValue.of(999d)       | 999d
        EnumValue.of("enumValue") | "enumValue"
        ObjectValue.newObjectValue()
                .objectField(ObjectField.newObjectField().name("a").value(IntValue.of(64)).build())
                .objectField(ObjectField.newObjectField().name("b").value(StringValue.of("65")).build())
                .build()          | [a: 64, b: "65"]
        ArrayValue.newArrayValue()
                .value(IntValue.of(9))
                .value(StringValue.of("10"))
                .value(EnumValue.of("enum"))
                .build()          | [9, "10", "enum"]

    }

    def "can handle NormalizedInputValue values that are literals"() {
        expect:
        def niv = new NormalizedInputValue("TypeName", value)
        def actual = ValueToVariableValueCompiler.normalisedValueToVariableValue(niv)
        actual == expected

        where:
        value                     | expected
        NullValue.of()            | null
        IntValue.of(666)          | 666
        StringValue.of("str")     | "str"
        BooleanValue.of(true)     | true
        FloatValue.of(999d)       | 999d
        EnumValue.of("enumValue") | "enumValue"
        ObjectValue.newObjectValue()
                .objectField(ObjectField.newObjectField().name("a").value(IntValue.of(64)).build())
                .objectField(ObjectField.newObjectField().name("b").value(StringValue.of("65")).build())
                .build()          | [a: 64, b: "65"]
        ArrayValue.newArrayValue()
                .value(IntValue.of(9))
                .value(StringValue.of("10"))
                .value(EnumValue.of("enum"))
                .build()          | [9, "10", "enum"]


    }

    def "can handle NormalizedInputValue values that are not literals"() {
        expect:
        def niv = new NormalizedInputValue("TypeName", value)
        def actual = ValueToVariableValueCompiler.normalisedValueToVariableValue(niv)
        actual == expected

        where:
        value                                      | expected
        null                                       | null
        [IntValue.of(666), IntValue.of(664)]       | [666, 664]
        [a: IntValue.of(666), b: IntValue.of(664)] | [a: 666, b: 664]

        // at present we dont handle straight up java objects like 123 because
        // the ValueResolver never produces them during
        // ValueResolver.getNormalizedVariableValues say - this is debatable behavior
        // but for now this is what we do
    }


    def "can print variables as expected"() {
        expect:
        def niv = new NormalizedInputValue(typeName, value)
        def actual = ValueToVariableValueCompiler.normalizedInputValueToVariable(niv, varCount)
        actual.value == expectedValue
        actual.variableReference.name == expectedVarName
        actual.definition.name == expectedVarName
        // compare actual type
        def actualType = actual.definition.type
        actualType.isEqualTo(expectedType)
        if(actualType instanceof NonNullType || actualType instanceof ListType){
            actualType.type.isEqualTo(expectedType.type)
        }
        TypeUtil.simplePrint(actualType) == typeName

        where:
        value                            | varCount | typeName     | expectedValue     | expectedVarName | expectedType
        NullValue.newNullValue().build() | 1        | "ID"         | null              | "v1"            | new TypeName("ID")
        IntValue.of(666)                 | 2        | "Int!"       | 666               | "v2"            | new NonNullType(new TypeName("Int"))
        StringValue.of("str")            | 3        | "String"     | "str"             | "v3"            | new TypeName("String")
        BooleanValue.of(true)            | 4        | "Boolean!"   | true              | "v4"            | new NonNullType(new TypeName("Boolean"))
        FloatValue.of(999d)              | 5        | "Float"      | 999d              | "v5"            | new TypeName("Float")
        EnumValue.of("enumValue")        | 6        | "Foo!"       | "enumValue"       | "v6"            | new NonNullType(new TypeName("Foo"))
        ["a" : IntValue.of(64),
         "b":IntValue.of(65) ]           | 7        | "ObjectType" | [a: 64, b: 65]    | "v7"            | new TypeName("ObjectType")
        [StringValue.of("9"),
         StringValue.of("10"),
         StringValue.of("11")]           | 8        | "[String]"   | ["9", "10", "11"] | "v8"            | new ListType((new TypeName("String")))

    }
}
