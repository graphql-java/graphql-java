package graphql.execution

import graphql.GraphQLException
import graphql.TestUtil
import graphql.language.Argument
import graphql.language.ArrayValue
import graphql.language.BooleanValue
import graphql.language.EnumValue
import graphql.language.IntValue
import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.ObjectField
import graphql.language.ObjectValue
import graphql.language.StringValue
import graphql.language.TypeName
import graphql.language.Value
import graphql.language.VariableDefinition
import graphql.language.VariableReference
import graphql.schema.CoercingParseValueException
import graphql.schema.GraphQLArgument
import spock.lang.Specification
import spock.lang.Unroll

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLFloat
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLEnumType.newEnum
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull

class ValuesResolverTest extends Specification {

    ValuesResolver resolver = new ValuesResolver()


    @Unroll
    def "getVariableValues: simple variable input #inputValue"() {
        given:
        def schema = TestUtil.schemaWithInputType(inputType)
        VariableDefinition variableDefinition = new VariableDefinition("variable", variableType)
        when:
        def resolvedValues = resolver.coerceArgumentValues(schema, [variableDefinition], [variable: inputValue])
        then:
        resolvedValues['variable'] == outputValue

        where:
        inputType      | variableType            | inputValue   || outputValue
        GraphQLInt     | new TypeName("Int")     | 100          || 100
        GraphQLString  | new TypeName("String")  | 'someString' || 'someString'
        GraphQLBoolean | new TypeName("Boolean") | 'true'       || true
        GraphQLFloat   | new TypeName("Float")   | '42.43'      || 42.43d

    }

    def "getVariableValues: map object as variable input"() {
        given:
        def nameField = newInputObjectField()
                .name("name")
                .type(GraphQLString)
        def idField = newInputObjectField()
                .name("id")
                .type(GraphQLInt)
        def inputType = newInputObject()
                .name("Person")
                .field(nameField)
                .field(idField)
                .build()
        def schema = TestUtil.schemaWithInputType(inputType)
        VariableDefinition variableDefinition = new VariableDefinition("variable", new TypeName("Person"))

        when:
        def resolvedValues = resolver.coerceArgumentValues(schema, [variableDefinition], [variable: inputValue])
        then:
        resolvedValues['variable'] == outputValue
        where:
        inputValue           || outputValue
        [name: 'a', id: 123] || [name: 'a', id: 123]
        [id: 123]            || [id: 123]
        [name: 'x']          || [name: 'x']
    }


    class Person {
        def name = ""
        def id = 0

        Person(name, id) {
            this.name = name
            this.id = id
        }

    }

    def "getVariableValues: object as variable input"() {
        given:
        def nameField = newInputObjectField()
                .name("name")
                .type(GraphQLString)
        def idField = newInputObjectField()
                .name("id")
                .type(GraphQLInt)
        def inputType = newInputObject()
                .name("Person")
                .field(nameField)
                .field(idField)
                .build()
        def schema = TestUtil.schemaWithInputType(inputType)
        VariableDefinition variableDefinition = new VariableDefinition("variable", new TypeName("Person"))

        when:
        def obj = new Person('a', 123)
        resolver.coerceArgumentValues(schema, [variableDefinition], [variable: obj])
        then:
        thrown(CoercingParseValueException)
    }

    def "getVariableValues: simple value gets resolved to a list when the type is a List"() {
        given:
        def schema = TestUtil.schemaWithInputType(list(GraphQLString))
        VariableDefinition variableDefinition = new VariableDefinition("variable", new ListType(new TypeName("String")))
        String value = "world"
        when:
        def resolvedValues = resolver.coerceArgumentValues(schema, [variableDefinition], [variable: value])
        then:
        resolvedValues['variable'] == ['world']

    }


    def "getArgumentValues: resolves argument with variable reference"() {
        given:
        def variables = [var: 'hello']
        def fieldArgument = new GraphQLArgument("arg", GraphQLString)
        def argument = new Argument("arg", new VariableReference("var"))

        when:
        def values = resolver.getArgumentValues([fieldArgument], [argument], variables)

        then:
        values['arg'] == 'hello'
    }

    def "getArgumentValues: resolves object literal"() {
        given: "schema defining input object"
        def subObjectType = newInputObject()
                .name("SubType")
                .field(newInputObjectField()
                .name("subKey")
                .type(GraphQLBoolean))
                .build()
        def inputObjectType = newInputObject()
                .name("inputObject")
                .field(newInputObjectField()
                .name("intKey")
                .type(GraphQLInt))
                .field(newInputObjectField()
                .name("stringKey")
                .type(GraphQLString))
                .field(newInputObjectField()
                .name("subObject")
                .type(subObjectType))
                .build()
        def fieldArgument = new GraphQLArgument("arg", inputObjectType)

        when:
        def argument = new Argument("arg", inputValue)
        def values = resolver.getArgumentValues([fieldArgument], [argument], [:])

        then:
        values['arg'] == outputValue

        where:
        inputValue << [
                buildObjectLiteral([
                        intKey   : new IntValue(BigInteger.ONE),
                        stringKey: new StringValue("world"),
                        subObject: [
                                subKey: new BooleanValue(true)
                        ]
                ]),
                buildObjectLiteral([
                        intKey   : new IntValue(BigInteger.ONE),
                        stringKey: new StringValue("world")
                ]),
                buildObjectLiteral([
                        intKey: new IntValue(BigInteger.ONE)
                ])
        ]
        outputValue << [
                [intKey: 1, stringKey: 'world', subObject: [subKey: true]],
                [intKey: 1, stringKey: 'world'],
                [intKey: 1]
        ]
    }

    def "getArgumentValues: uses default value if object literal omits field"() {
        given: "schema defining input object"
        def inputObjectType = newInputObject()
                .name("inputObject")
                .field(newInputObjectField()
                .name("intKey")
                .type(nonNull(GraphQLInt))
                .defaultValue(3)
                .build())
                .field(newInputObjectField()
                .name("stringKey")
                .type(GraphQLString)
                .defaultValue("defaultString")
                .build())
                .build()
        def fieldArgument = new GraphQLArgument("arg", inputObjectType)

        when:
        def argument = new Argument("arg", inputValue)
        def values = resolver.getArgumentValues([fieldArgument], [argument], [:])

        then:
        values['arg'] == outputValue

        where:
        inputValue << [
                buildObjectLiteral([
                        intKey   : new IntValue(BigInteger.ONE),
                        stringKey: new StringValue("world")
                ]),
                buildObjectLiteral([
                        intKey: new IntValue(BigInteger.ONE)
                ]),
                buildObjectLiteral([:])
        ]
        outputValue << [
                [intKey: 1, stringKey: 'world'],
                [intKey: 1, stringKey: 'defaultString'],
                [intKey: 3, stringKey: 'defaultString']
        ]
    }

    def "getArgumentValues: missing InputObject fields which are non-null cause error"() {
        given: "schema defining input object"
        def inputObjectType = newInputObject()
                .name("inputObject")
                .field(newInputObjectField()
                .name("intKey")
                .type(nonNull(GraphQLInt))
                .build())
                .build()
        def fieldArgument = new GraphQLArgument("arg", inputObjectType)

        when:
        def argument = new Argument("arg", ObjectValue.newObjectValue().build())
        resolver.getArgumentValues([fieldArgument], [argument], [:])

        then:
        thrown(GraphQLException)
    }

    ObjectValue buildObjectLiteral(Map<String, Object> contents) {
        def object = ObjectValue.newObjectValue()
        contents.each { key, value ->
            def transformedValue = value instanceof Map ? buildObjectLiteral(value) : (Value) value
            object.objectField(new ObjectField(key, transformedValue))
        }
        return object.build()
    }

    def "getArgumentValues: resolves enum literals"() {
        given: "the ast"
        EnumValue enumValue1 = new EnumValue("PLUTO")
        EnumValue enumValue2 = new EnumValue("MARS")
        def argument1 = new Argument("arg1", enumValue1)
        def argument2 = new Argument("arg2", enumValue2)

        and: "the schema"
        def enumType = newEnum()
                .name("EnumType")
                .value("PLUTO")
                .value("MARS", "mars")
                .build()
        def fieldArgument1 = new GraphQLArgument("arg1", enumType)
        def fieldArgument2 = new GraphQLArgument("arg2", enumType)
        when:
        def values = resolver.getArgumentValues([fieldArgument1, fieldArgument2], [argument1, argument2], [:])

        then:
        values['arg1'] == 'PLUTO'
        values['arg2'] == 'mars'
    }

    def "getArgumentValues: resolves array literals"() {
        given:
        ArrayValue.Builder arrayValue = ArrayValue.newArrayValue()
        arrayValue.value(new BooleanValue(true))
        arrayValue.value(new BooleanValue(false))
        def argument = new Argument("arg", arrayValue.build())

        def fieldArgument = new GraphQLArgument("arg", list(GraphQLBoolean))

        when:
        def values = resolver.getArgumentValues([fieldArgument], [argument], [:])

        then:
        values['arg'] == [true, false]

    }

    def "getArgumentValues: resolves single value literal to a list when type is a list "() {
        given:
        StringValue stringValue = new StringValue("world")
        def argument = new Argument("arg", stringValue)

        def fieldArgument = new GraphQLArgument("arg", list(GraphQLString))

        when:
        def values = resolver.getArgumentValues([fieldArgument], [argument], [:])

        then:
        values['arg'] == ['world']

    }

    def "getVariableValues: enum as variable input"() {
        given:
        def enumDef = newEnum()
                .name("Test")
                .value("A_TEST")
                .value("VALUE_TEST", 1)
                .build()

        def schema = TestUtil.schemaWithInputType(enumDef)
        VariableDefinition variableDefinition = new VariableDefinition("variable", new TypeName("Test"))

        when:
        def resolvedValues = resolver.coerceArgumentValues(schema, [variableDefinition], [variable: inputValue])
        then:
        resolvedValues['variable'] == outputValue
        where:
        inputValue   || outputValue
        "A_TEST"     || "A_TEST"
        "VALUE_TEST" || 1

    }

    def "getVariableValues: input object with non-required fields and default values"() {
        given:

        def inputObjectType = newInputObject()
                .name("InputObject")
                .field(newInputObjectField()
                .name("intKey")
                .type(GraphQLInt))
                .field(newInputObjectField()
                .name("stringKey")
                .type(GraphQLString)
                .defaultValue("defaultString"))
                .build()

        def schema = TestUtil.schemaWithInputType(inputObjectType)
        VariableDefinition variableDefinition = new VariableDefinition("variable", new TypeName("InputObject"))

        when:
        def resolvedValues = resolver.coerceArgumentValues(schema, [variableDefinition], [variable: inputValue])

        then:
        resolvedValues['variable'] == outputValue

        where:
        inputValue                    || outputValue
        [intKey: 10]                  || [intKey: 10, stringKey: 'defaultString']
        [intKey: 10, stringKey: null] || [intKey: 10, stringKey: 'defaultString']

    }

    def "getVariableInput: Missing InputObject fields which are non-null cause error"() {

        given:
        def inputObjectType = newInputObject()
                .name("InputObject")
                .field(newInputObjectField()
                .name("intKey")
                .type(GraphQLInt))
                .field(newInputObjectField()
                .name("requiredField")
                .type(nonNull(GraphQLString)))
                .build()

        def schema = TestUtil.schemaWithInputType(inputObjectType)
        VariableDefinition variableDefinition = new VariableDefinition("variable", new TypeName("InputObject"))

        when:
        resolver.coerceArgumentValues(schema, [variableDefinition], [variable: inputValue])

        then:
        thrown(GraphQLException)

        where:
        inputValue                        | _
        [intKey: 10]                      | _
        [intKey: 10, requiredField: null] | _
    }

    def "getVariableValues: simple types with values not provided in variables map"() {
        given:

        def schema = TestUtil.schemaWithInputType(GraphQLString)
        VariableDefinition fooVarDef = new VariableDefinition("foo", new TypeName("String"))
        VariableDefinition barVarDef = new VariableDefinition("bar", new TypeName("String"))

        when:
        def resolvedValues = resolver.coerceArgumentValues(schema, [fooVarDef, barVarDef], InputValue)

        then:
        resolvedValues == outputValue

        where:
        InputValue                || outputValue
        [foo: "added", bar: null] || [foo: "added", bar: null]
        [foo: "added"]            || [foo: "added"]
    }

    def "getVariableValues: null default value for non null type"() {
        given:

        def schema = TestUtil.schemaWithInputType(nonNull(GraphQLString))
        VariableDefinition fooVarDef = new VariableDefinition("foo", new NonNullType(new TypeName("String")))

        when:
        resolver.coerceArgumentValues(schema, [fooVarDef], [:])

        then:
        thrown(GraphQLException)
    }
}
