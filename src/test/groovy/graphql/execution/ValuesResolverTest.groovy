package graphql.execution

import graphql.GraphQLException
import graphql.TestUtil
import graphql.language.*
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import spock.lang.Specification
import spock.lang.Unroll

import static graphql.Scalars.*
import static graphql.schema.GraphQLEnumType.newEnum
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject

class ValuesResolverTest extends Specification {

    ValuesResolver resolver = new ValuesResolver()


    @Unroll
    def "getVariableValues: simple variable input #inputValue"() {
        given:
        def schema = TestUtil.schemaWithInputType(inputType)
        VariableDefinition variableDefinition = new VariableDefinition("variable", variableType)
        when:
        def resolvedValues = resolver.getVariableValues(schema, [variableDefinition], [variable: inputValue])
        then:
        resolvedValues['variable'] == outputValue

        where:
        inputType      | variableType            | inputValue   || outputValue
        GraphQLInt     | new TypeName("Int")     | 100          || 100
        GraphQLString  | new TypeName("String")  | 'someString' || 'someString'
        GraphQLBoolean | new TypeName("Boolean") | 'true'       || true
        GraphQLFloat | new TypeName("Float") | '42.43' || 42.43d

    }

    def "getVariableValues: object as variable input"() {
        given:
        def nameField = newInputObjectField()
                .name("name")
                .type(GraphQLString)
                .build()
        def idField = newInputObjectField()
                .name("id")
                .type(GraphQLInt)
                .build()
        def inputType = newInputObject()
                .name("Person")
                .field(nameField)
                .field(idField)
                .build()
        def schema = TestUtil.schemaWithInputType(inputType)
        VariableDefinition variableDefinition = new VariableDefinition("variable", new TypeName("Person"))

        when:
        def resolvedValues = resolver.getVariableValues(schema, [variableDefinition], [variable: [name: 'a', id: 123]])
        then:
        resolvedValues['variable'] == [name: 'a', id: 123]
    }

    def "getVariableValues: simple value gets resolved to a list when the type is a List"() {
        given:
        def schema = TestUtil.schemaWithInputType(new GraphQLList(GraphQLString))
        VariableDefinition variableDefinition = new VariableDefinition("variable", new ListType(new TypeName("String")))
        String value = "world"
        when:
        def resolvedValues = resolver.getVariableValues(schema, [variableDefinition], [variable: value])
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
        given: "complex object value"
        def complexObjectValue = new ObjectValue()
        complexObjectValue.getObjectFields().add(new ObjectField("intKey", new IntValue("1")))
        complexObjectValue.getObjectFields().add(new ObjectField("stringKey", new StringValue("world")))
        def subObject = new ObjectValue()
        subObject.getObjectFields().add(new ObjectField("subKey", new BooleanValue(true)))
        complexObjectValue.getObjectFields().add(new ObjectField("subObject", subObject))
        def argument = new Argument("arg", complexObjectValue)

        and: "schema defining input object"
        def subObjectType = newInputObject()
                .name("SubType")
                .field(newInputObjectField()
                .name("subKey")
                .type(GraphQLBoolean)
                .build())
                .build()
        def inputObjectType = newInputObject()
                .name("inputObject")
                .field(newInputObjectField()
                .name("intKey")
                .type(GraphQLInt)
                .build())
                .field(newInputObjectField()
                .name("stringKey")
                .type(GraphQLString)
                .build())
                .field(newInputObjectField()
                .name("subObject")
                .type(subObjectType)
                .build())
                .build()
        def fieldArgument = new GraphQLArgument("arg", inputObjectType)

        when:
        def values = resolver.getArgumentValues([fieldArgument], [argument], [:])

        then:
        values['arg'] == [intKey: 1, stringKey: 'world', subObject: [subKey: true]]
    }

    def "getArgumentValues: resolves enum literals"() {
        given: "the ast"
        EnumValue enumValue1 = new EnumValue("PLUTO");
        EnumValue enumValue2 = new EnumValue("MARS");
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
        ArrayValue arrayValue = new ArrayValue()
        arrayValue.getValues().add(new BooleanValue(true))
        arrayValue.getValues().add(new BooleanValue(false))
        def argument = new Argument("arg", arrayValue)

        def fieldArgument = new GraphQLArgument("arg", new GraphQLList(GraphQLBoolean))

        when:
        def values = resolver.getArgumentValues([fieldArgument], [argument], [:])

        then:
        values['arg'] == [true, false]

    }

    def "getArgumentValues: resolves single value literal to a list when type is a list "() {
        given:
        StringValue stringValue = new StringValue("world")
        def argument = new Argument("arg", stringValue)

        def fieldArgument = new GraphQLArgument("arg", new GraphQLList(GraphQLString))

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
        def resolvedValues = resolver.getVariableValues(schema, [variableDefinition], [variable: inputValue])
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
                .type(GraphQLInt)
                .build())
                .field(newInputObjectField()
                .name("stringKey")
                .type(GraphQLString)
                .defaultValue("defaultString")
                .build())
                .build()
        def inputValue = [intKey: 10]

        def schema = TestUtil.schemaWithInputType(inputObjectType)
        VariableDefinition variableDefinition = new VariableDefinition("variable", new TypeName("InputObject"))

        when:
        def resolvedValues = resolver.getVariableValues(schema, [variableDefinition], [variable: inputValue])

        then:
        resolvedValues['variable'].stringKey == 'defaultString'
        resolvedValues['variable'].intKey == 10
    }

    def "getVariableInput: Missing InputObject fields which are non-null cause error"() {

        given:
        def inputObjectType = newInputObject()
                .name("InputObject")
                .field(newInputObjectField()
                .name("intKey")
                .type(GraphQLInt)
                .build())
                .field(newInputObjectField()
                .name("requiredField")
                .type(new GraphQLNonNull(GraphQLString))
                .build())
                .build()
        def inputValue = [intKey: 10]

        def schema = TestUtil.schemaWithInputType(inputObjectType)
        VariableDefinition variableDefinition = new VariableDefinition("variable", new TypeName("InputObject"))

        when:
        def resolvedValues = resolver.getVariableValues(schema, [variableDefinition], [variable: inputValue])

        then:
        thrown(GraphQLException)
    }
}
