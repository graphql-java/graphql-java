package graphql.schema.idl


import graphql.TestUtil
import graphql.schema.GraphQLAppliedArgument
import graphql.schema.GraphQLArgument
import spock.lang.Specification

import static graphql.Scalars.GraphQLString

class SchemaGeneratorAppliedDirectiveHelperTest extends Specification {

    def sdl = """
            directive @foo(arg1 : String! = "fooArg1Value", arg2 : String) on 
            SCHEMA | SCALAR | OBJECT | FIELD_DEFINITION | ARGUMENT_DEFINITION |
            INTERFACE | UNION |  ENUM | ENUM_VALUE | INPUT_OBJECT | INPUT_FIELD_DEFINITION

            directive @bar(arg1 : String, arg2 : String) on 
            SCHEMA | SCALAR | OBJECT | FIELD_DEFINITION | ARGUMENT_DEFINITION |
            INTERFACE | UNION |  ENUM | ENUM_VALUE | INPUT_OBJECT | INPUT_FIELD_DEFINITION

            type Query {
                field : Bar @foo(arg2 : "arg2Value") @bar(arg1 : "barArg1Value" arg2 : "arg2Value")
            }
            
            type Bar @foo(arg1 : "BarTypeValue" arg2 : "arg2Value") {
                bar : String
            }
        """

    def "can capture applied directives and legacy directives"() {

        when:
        def schema = TestUtil.schema(sdl)
        def field = schema.getObjectType("Query").getField("field")
        def barType = schema.getObjectType("Bar")

        then:

        field.directives.collect { it.name }.sort() == ["bar", "foo"]
        field.appliedDirectives.collect { it.name }.sort() == ["bar", "foo"]

        barType.directives.collect { it.name }.sort() == ["foo"]
        barType.appliedDirectives.collect { it.name }.sort() == ["foo"]


        def fooDirective = field.getDirective("foo")
        fooDirective.arguments.collect { it.name }.sort() == ["arg1", "arg2"]
        fooDirective.arguments.collect { GraphQLArgument.getArgumentValue(it) }.sort() == ["arg2Value", "fooArg1Value",]

        def fooAppliedDirective = field.getAppliedDirective("foo")
        fooAppliedDirective.arguments.collect { it.name }.sort() == ["arg1", "arg2"]
        fooAppliedDirective.arguments.collect { GraphQLAppliedArgument.getArgumentValue(it, GraphQLString) }.sort() == ["arg2Value", "fooArg1Value"]


        def fooDirectiveOnType = barType.getDirective("foo")
        fooDirectiveOnType.arguments.collect { it.name }.sort() == ["arg1", "arg2"]
        fooDirectiveOnType.arguments.collect { GraphQLArgument.getArgumentValue(it) }.sort() == ["BarTypeValue", "arg2Value",]

        def fooAppliedDirectiveOnType = barType.getAppliedDirective("foo")
        fooAppliedDirectiveOnType.arguments.collect { it.name }.sort() == ["arg1", "arg2"]
        fooAppliedDirectiveOnType.arguments.collect { GraphQLAppliedArgument.getArgumentValue(it, GraphQLString) }.sort() == ["BarTypeValue", "arg2Value",]

    }
}
