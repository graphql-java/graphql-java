package graphql.execution

import graphql.Scalars
import graphql.TestUtil
import graphql.language.Argument
import graphql.language.BooleanValue
import graphql.language.EnumValue
import graphql.language.IntValue
import graphql.language.ObjectField
import graphql.language.ObjectValue
import graphql.language.StringValue
import graphql.language.TypeName
import graphql.language.VariableDefinition
import graphql.language.VariableReference
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLFieldArgument
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import spock.lang.Specification
import spock.lang.Unroll

import static graphql.schema.GraphQLInputObjectField.*
import static graphql.schema.GraphQLInputObjectType.*


class ValuesResolverTest extends Specification {

    ValuesResolver resolver = new ValuesResolver()


    @Unroll
    def "simple variable input #inputValue"() {
        given:
        def schema = TestUtil.schemaWithInputType(inputType)
        VariableDefinition variableDefinition = new VariableDefinition("variable", variableType)
        when:
        def resolvedValues = resolver.getVariableValues(schema, [variableDefinition], [variable: inputValue])
        then:
        resolvedValues['variable'] == outputValue

        where:
        inputType              | variableType            | inputValue   || outputValue
        Scalars.GraphQLInt     | new TypeName("Int")     | 100          || 100
        Scalars.GraphQLString  | new TypeName("String")  | 'someString' || 'someString'
        Scalars.GraphQLBoolean | new TypeName("Boolean") | 'true'       || true
        Scalars.GraphQLFloat   | new TypeName("Float")   | '42.43'      || 42.43f

    }

    def "object as variable input"() {
        given:
        def nameField = newInputObjectField()
                .name("name")
                .type(Scalars.GraphQLString)
                .build()
        def idField = newInputObjectField()
                .name("id")
                .type(Scalars.GraphQLInt)
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


    def "resolves argument with variable reference"() {
        given:
        def variables = [var: 'hello']
        def fieldArgument = new GraphQLFieldArgument("arg", Scalars.GraphQLString)
        def argument = new Argument("arg", new VariableReference("var"))

        when:
        def values = resolver.getArgumentValues([fieldArgument], [argument], variables)

        then:
        values['arg'] == 'hello'
    }

    def "resolves object literal"() {
        given: "complex object value"
        def complexObjectValue = new ObjectValue()
        complexObjectValue.getObjectFields().add(new ObjectField("intKey", new IntValue(1)))
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
                .type(Scalars.GraphQLBoolean)
                .build())
                .build()
        def inputObjectType = newInputObject()
                .name("inputObject")
                .field(newInputObjectField()
                .name("intKey")
                .type(Scalars.GraphQLInt)
                .build())
                .field(newInputObjectField()
                .name("stringKey")
                .type(Scalars.GraphQLString)
                .build())
                .field(newInputObjectField()
                .name("subObject")
                .type(subObjectType)
                .build())
                .build()
        def fieldArgument = new GraphQLFieldArgument("arg", inputObjectType)

        when:
        def values = resolver.getArgumentValues([fieldArgument], [argument], [:])

        then:
        values['arg'] == [intKey: 1, stringKey: 'world', subObject: [subKey: true]]
    }

    def "resolves enum literals"() {
        given: "the ast"
        EnumValue enumValue1 = new EnumValue("PLUTO");
        EnumValue enumValue2 = new EnumValue("MARS");
        def argument1 = new Argument("arg1", enumValue1)
        def argument2 = new Argument("arg2", enumValue2)

        and: "the schema"
        def enumType = GraphQLEnumType.newEnum()
                .name("EnumType")
                .value("PLUTO")
                .value("MARS", "mars")
                .build()
        def fieldArgument1 = new GraphQLFieldArgument("arg1", enumType)
        def fieldArgumen2 = new GraphQLFieldArgument("arg2", enumType)
        when:
        def values = resolver.getArgumentValues([fieldArgument1, fieldArgumen2], [argument1, argument2], [:])

        then:
        values['arg1'] == 'PLUTO'
        values['arg2'] == 'mars'

    }
}
