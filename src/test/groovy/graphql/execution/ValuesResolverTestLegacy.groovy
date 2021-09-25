package graphql.language


import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLInputObjectType
import spock.lang.Ignore
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLFloat
import static graphql.Scalars.GraphQLID
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.execution.ValuesResolver.valueToLiteralLegacy
import static graphql.language.BooleanValue.newBooleanValue
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull

class ValuesResolverTestLegacy extends Specification {

    def 'converts boolean values to ASTs'() {
        expect:
        valueToLiteralLegacy(true, GraphQLBoolean).isEqualTo(newBooleanValue(true).build())

        valueToLiteralLegacy(false, GraphQLBoolean).isEqualTo(newBooleanValue(false).build())

        valueToLiteralLegacy(null, GraphQLBoolean) == null

        valueToLiteralLegacy(0, GraphQLBoolean).isEqualTo(newBooleanValue(false).build())

        valueToLiteralLegacy(1, GraphQLBoolean).isEqualTo(newBooleanValue(true).build())

        def NonNullBoolean = nonNull(GraphQLBoolean)
        valueToLiteralLegacy(0, NonNullBoolean).isEqualTo(newBooleanValue(false).build())
    }

    BigInteger bigInt(int i) {
        return new BigInteger(String.valueOf(i))
    }

    def 'converts Int values to Int ASTs'() {
        expect:
        valueToLiteralLegacy(123.0, GraphQLInt).isEqualTo(IntValue.newIntValue(bigInt(123)).build())

        valueToLiteralLegacy(1e4, GraphQLInt).isEqualTo(IntValue.newIntValue(bigInt(10000)).build())
    }

    def 'converts Float values to Int/Float ASTs'() {
        expect:
        valueToLiteralLegacy(123.0, GraphQLFloat).isEqualTo(FloatValue.newFloatValue(123.0).build())

        valueToLiteralLegacy(123.5, GraphQLFloat).isEqualTo(FloatValue.newFloatValue(123.5).build())

        valueToLiteralLegacy(1e4, GraphQLFloat).isEqualTo(FloatValue.newFloatValue(10000.0).build())

        valueToLiteralLegacy(1e40, GraphQLFloat).isEqualTo(FloatValue.newFloatValue(1.0e40).build())
    }


    def 'converts String values to String ASTs'() {
        expect:
        valueToLiteralLegacy('hello', GraphQLString).isEqualTo(new StringValue('hello'))

        valueToLiteralLegacy('VALUE', GraphQLString).isEqualTo(new StringValue('VALUE'))

        valueToLiteralLegacy('VA\n\t\f\r\b\\LUE', GraphQLString).isEqualTo(new StringValue('VA\n\t\f\r\b\\LUE'))

        valueToLiteralLegacy('VA\\L\"UE', GraphQLString).isEqualTo(new StringValue('VA\\L\"UE'))

        valueToLiteralLegacy(123, GraphQLString).isEqualTo(new StringValue('123'))

        valueToLiteralLegacy(false, GraphQLString).isEqualTo(new StringValue('false'))

        valueToLiteralLegacy(null, GraphQLString) == null
    }

    def 'converts ID values to Int/String ASTs'() {
        expect:
        valueToLiteralLegacy('hello', GraphQLID).isEqualTo(new StringValue('hello'))

        valueToLiteralLegacy('VALUE', GraphQLID).isEqualTo(new StringValue('VALUE'))

        // Note: EnumValues cannot contain non-identifier characters
        valueToLiteralLegacy('VA\nLUE', GraphQLID).isEqualTo(new StringValue('VA\nLUE'))

        // Note: IntValues are used when possible.
        valueToLiteralLegacy(123, GraphQLID).isEqualTo(new IntValue(bigInt(123)))

        valueToLiteralLegacy(null, GraphQLID) == null
    }


    def 'does not converts NonNull values to NullValue'() {
        expect:
        def NonNullBoolean = nonNull(GraphQLBoolean)
        valueToLiteralLegacy(null, NonNullBoolean) == null
    }

    def complexValue = { someArbitrary: 'complexValue' }


    def myEnum = GraphQLEnumType.newEnum().name('MyEnum')
            .value('HELLO')
            .value('GOODBYE')
            .value('COMPLEX', complexValue)
            .build()

    def 'converts string values to Enum ASTs if possible'() {
        expect:
        valueToLiteralLegacy('HELLO', myEnum).isEqualTo(new EnumValue('HELLO'))

        valueToLiteralLegacy(complexValue, myEnum).isEqualTo(new EnumValue('COMPLEX'))
    }

    def 'converts array values to List ASTs'() {
        expect:
        valueToLiteralLegacy(['FOO', 'BAR'], list(GraphQLString)).isEqualTo(
                new ArrayValue([new StringValue('FOO'), new StringValue('BAR')])
        )


        valueToLiteralLegacy(['HELLO', 'GOODBYE'], list(myEnum)).isEqualTo(
                new ArrayValue([new EnumValue('HELLO'), new EnumValue('GOODBYE')])
        )
    }

    def 'converts list singletons'() {
        expect:
        valueToLiteralLegacy('FOO', list(GraphQLString)).isEqualTo(
                new StringValue('FOO')
        )
    }

    def 'converts list to lists'() {
        expect:
        valueToLiteralLegacy(['hello', 'world'], list(GraphQLString)).isEqualTo(
                new ArrayValue(['hello', 'world'])
        )
    }

    def 'converts arrays to lists'() {
        String[] sArr = ['hello', 'world'] as String[]
        expect:
        valueToLiteralLegacy(sArr, list(GraphQLString)).isEqualTo(
                new ArrayValue(['hello', 'world'])
        )
    }

    class SomePojo {
        def foo = 3
        def bar = "HELLO"
    }

    class SomePojoWithFields {
        public float foo = 3
        public String bar = "HELLO"
    }

    def 'converts input objects'() {
        given:
        def inputObj = GraphQLInputObjectType.newInputObject()
                .name('MyInputObj')
                .field({ f -> f.name("foo").type(GraphQLFloat) })
                .field({ f -> f.name("bar").type(myEnum) })
                .build()
        expect:

        valueToLiteralLegacy([foo: 3, bar: 'HELLO'], inputObj).isEqualTo(
                new ObjectValue([new ObjectField("foo", new IntValue(bigInt(3))),
                                 new ObjectField("bar", new EnumValue('HELLO')),
                ])
        )

        valueToLiteralLegacy(new SomePojo(), inputObj).isEqualTo(
                new ObjectValue([new ObjectField("foo", new IntValue(bigInt(3))),
                                 new ObjectField("bar", new EnumValue('HELLO')),
                ])
        )

        valueToLiteralLegacy(new SomePojoWithFields(), inputObj).isEqualTo(
                new ObjectValue([new ObjectField("foo", new IntValue(bigInt(3))),
                                 new ObjectField("bar", new EnumValue('HELLO')),
                ])
        )


    }

    @Ignore("ObjectValue.isEqualTo is broken - this test currently makes no sense")
    def 'converts input objects with explicit nulls'() {
        expect:
        def inputObj = GraphQLInputObjectType.newInputObject()
                .name('MyInputObj')
                .field({ f -> f.name("foo").type(GraphQLFloat) })
                .field({ f -> f.name("bar").type(myEnum) })
                .build()

        valueToLiteralLegacy([foo: null], inputObj).isEqualTo(
                new ObjectValue([new ObjectField("foo", null)])
        )
    }


}
