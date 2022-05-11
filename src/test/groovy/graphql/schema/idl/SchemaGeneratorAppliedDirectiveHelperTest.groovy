package graphql.schema.idl


import graphql.TestUtil
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLInputType
import spock.lang.Specification

class SchemaGeneratorAppliedDirectiveHelperTest extends Specification {

    def sdl = """
            directive @foo(arg1 : String! = "fooArg1Value", arg2 : String) on 
                SCHEMA | SCALAR | OBJECT | FIELD_DEFINITION | ARGUMENT_DEFINITION |
                INTERFACE | UNION |  ENUM | ENUM_VALUE | INPUT_OBJECT | INPUT_FIELD_DEFINITION

            directive @bar(arg1 : String, arg2 : String) on 
                SCHEMA | SCALAR | OBJECT | FIELD_DEFINITION | ARGUMENT_DEFINITION |
                INTERFACE | UNION |  ENUM | ENUM_VALUE | INPUT_OBJECT | INPUT_FIELD_DEFINITION

            directive @complex(complexArg1 : ComplexInput! = { name : "Boris", address : { number : 10 street : "Downing St", town : "London" }}) on 
                SCHEMA | SCALAR | OBJECT | FIELD_DEFINITION | ARGUMENT_DEFINITION |
                INTERFACE | UNION |  ENUM | ENUM_VALUE | INPUT_OBJECT | INPUT_FIELD_DEFINITION

            type Query {
                field : Bar @foo(arg2 : "arg2Value") @bar(arg1 : "barArg1Value" arg2 : "arg2Value")
                complexField : Bar @complex
            }
            
            type Bar @foo(arg1 : "BarTypeValue" arg2 : "arg2Value") {
                bar : String
            }
            
            input ComplexInput {
                name : String
                address : Address
            }
            
            input Address {
                number : Int
                street : String
                town : String
            }
                
        """

    def "can capture applied directives and legacy directives"() {

        when:
        def schema = TestUtil.schema(sdl)
        def field = schema.getObjectType("Query").getField("field")
        def complexField = schema.getObjectType("Query").getField("complexField")
        def barType = schema.getObjectType("Bar")

        then:

        schema.getDirectives().collect {it.name}.sort() == [
                "bar",
                "complex",
                "deprecated",
                "foo",
                "include",
                "skip",
                "specifiedBy",
        ]

        field.directives.collect { it.name }.sort() == ["bar", "foo"]
        field.appliedDirectives.collect { it.name }.sort() == ["bar", "foo"]

        barType.directives.collect { it.name }.sort() == ["foo"]
        barType.appliedDirectives.collect { it.name }.sort() == ["foo"]


        def fooDirective = field.getDirective("foo")
        fooDirective.arguments.collect { it.name }.sort() == ["arg1", "arg2"]
        fooDirective.arguments.collect { GraphQLArgument.getArgumentValue(it) }.sort() == ["arg2Value", "fooArg1Value",]

        def fooAppliedDirective = field.getAppliedDirective("foo")
        fooAppliedDirective.arguments.collect { it.name }.sort() == ["arg1", "arg2"]
        fooAppliedDirective.arguments.collect { it.getValue() }.sort() == ["arg2Value", "fooArg1Value"]


        def fooDirectiveOnType = barType.getDirective("foo")
        fooDirectiveOnType.arguments.collect { it.name }.sort() == ["arg1", "arg2"]
        fooDirectiveOnType.arguments.collect { GraphQLArgument.getArgumentValue(it) }.sort() == ["BarTypeValue", "arg2Value",]

        def fooAppliedDirectiveOnType = barType.getAppliedDirective("foo")
        fooAppliedDirectiveOnType.arguments.collect { it.name }.sort() == ["arg1", "arg2"]
        fooAppliedDirectiveOnType.arguments.collect { it.getValue() }.sort() == ["BarTypeValue", "arg2Value",]


        def complexDirective = complexField.getDirective("complex")
        complexDirective.arguments.collect { it.name }.sort() == ["complexArg1"]
        complexDirective.arguments.collect { GraphQLArgument.getArgumentValue(it) }.sort() == [
                [name:"Boris", address:[number:10, street:"Downing St", town:"London"]]
        ]

        def complexAppliedDirective = complexField.getAppliedDirective("complex")
        GraphQLInputType complexInputType = schema.getTypeAs("ComplexInput")
        complexAppliedDirective.arguments.collect { it.name }.sort() == ["complexArg1"]
        complexAppliedDirective.arguments.collect { it.getValue() }.sort() == [
                [name:"Boris", address:[number:10, street:"Downing St", town:"London"]]
        ]

    }


    def "can capture ONLY applied directives"() {

        when:
        def options = SchemaGenerator.Options.defaultOptions()
        then:
        !options.isUseAppliedDirectivesOnly() // default is capture both


        when:
        options = SchemaGenerator.Options.defaultOptions().useAppliedDirectivesOnly(true)

        def schema = TestUtil.schema(options, sdl, RuntimeWiring.MOCKED_WIRING)
        def field = schema.getObjectType("Query").getField("field")
        def barType = schema.getObjectType("Bar")

        then:

        schema.getDirectives().collect {it.name}.sort() == [
                "bar",
                "complex",
                "deprecated",
                "foo",
                "include",
                "skip",
                "specifiedBy",
        ]

        field.directives.collect { it.name }.sort() == []
        field.appliedDirectives.collect { it.name }.sort() == ["bar", "foo"]

        barType.directives.collect { it.name }.sort() == []
        barType.appliedDirectives.collect { it.name }.sort() == ["foo"]


        def fooAppliedDirective = field.getAppliedDirective("foo")
        fooAppliedDirective.arguments.collect { it.name }.sort() == ["arg1", "arg2"]
        fooAppliedDirective.arguments.collect { it.value }.sort() == ["arg2Value", "fooArg1Value"]
    }

}
