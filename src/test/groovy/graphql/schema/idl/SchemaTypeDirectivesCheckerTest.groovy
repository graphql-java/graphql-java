package graphql.schema.idl

import graphql.Scalars
import graphql.schema.GraphQLScalarType
import graphql.schema.idl.errors.DirectiveIllegalLocationError
import graphql.schema.idl.errors.DirectiveMissingNonNullArgumentError
import graphql.schema.idl.errors.DirectiveUndeclaredError
import graphql.schema.idl.errors.DirectiveUnknownArgumentError
import graphql.schema.idl.errors.IllegalNameError
import graphql.schema.idl.errors.NotAnInputTypeError
import graphql.schema.idl.errors.DirectiveIllegalReferenceError
import spock.lang.Specification

class SchemaTypeDirectivesCheckerTest extends Specification {

    TypeDefinitionRegistry parse(String spec) {
        new SchemaParser().parse(spec)
    }

    def "legal directives"() {

        def spec = '''

            directive @testDirective(knownArg : String = "defaultValue") on SCHEMA | SCALAR | 
                                        OBJECT | FIELD_DEFINITION |
                                        ARGUMENT_DEFINITION | INTERFACE | UNION | 
                                        ENUM | ENUM_VALUE | 
                                        INPUT_OBJECT | INPUT_FIELD_DEFINITION
                                        

            type ObjectType @testDirective(knownArg : "x") {
                field(arg1 : String @testDirective(knownArg : "x")) : String @testDirective(knownArg : "x")
            }
            
            interface InterfaceType @testDirective(knownArg : "x") {
                field(arg1 : String @testDirective(knownArg : "x")) : String @testDirective(knownArg : "x")
            }

            union UnionType @testDirective(knownArg : "x") =  Foo | Bar
            
            enum EnumType @testDirective(knownArg : "x") {
                val1 @testDirective(knownArg : "x")
            }
            
            scalar ScalarType @testDirective(knownArg : "x")
            
            input InputType @testDirective(knownArg : "x") {
                field : String @testDirective(knownArg : "x")
            }
            

        '''
        def registry = parse(spec)
        def errors = []

        when:
        new SchemaTypeDirectivesChecker(registry, RuntimeWiring.newRuntimeWiring().build()).checkTypeDirectives(errors)

        then:
        errors.size() == 0
    }


    def "find undeclared directives"() {

        def spec = '''

            type ObjectType @testDirective {
                field(arg1 : String @testDirective) : String @testDirective
            }
            
            interface InterfaceType @testDirective {
                field(arg1 : String @testDirective) : String @testDirective
            }

            union UnionType @testDirective =  Foo | Bar
            
            enum EnumType @testDirective {
                val1 @testDirective
            }
            
            scalar ScalarType @testDirective
            
            input InputType @testDirective {
                field : String @testDirective
            }
            

        '''
        def registry = parse(spec)
        def errors = []

        when:
        new SchemaTypeDirectivesChecker(registry, RuntimeWiring.newRuntimeWiring().build()).checkTypeDirectives(errors)

        then:
        errors.each { assert it instanceof DirectiveUndeclaredError }
        errors.size() == 12
    }

    def "find illegal args directives"() {

        def spec = '''

            directive @testDirective on SCHEMA | SCALAR | 
                                        OBJECT | FIELD_DEFINITION |
                                        ARGUMENT_DEFINITION | INTERFACE | UNION | 
                                        ENUM | ENUM_VALUE | 
                                        INPUT_OBJECT | INPUT_FIELD_DEFINITION
                                        

            type ObjectType @testDirective(unknownArg : "x") {
                field(arg1 : String @testDirective(unknownArg : "x")) : String @testDirective(unknownArg : "x")
            }
            
            interface InterfaceType @testDirective(unknownArg : "x") {
                field(arg1 : String @testDirective(unknownArg : "x")) : String @testDirective(unknownArg : "x")
            }

            union UnionType @testDirective(unknownArg : "x") =  Foo | Bar
            
            enum EnumType @testDirective(unknownArg : "x") {
                val1 @testDirective(unknownArg : "x")
            }
            
            scalar ScalarType @testDirective(unknownArg : "x")
            
            input InputType @testDirective(unknownArg : "x") {
                field : String @testDirective(unknownArg : "x")
            }
            

        '''
        def registry = parse(spec)
        def errors = []

        when:
        new SchemaTypeDirectivesChecker(registry, RuntimeWiring.newRuntimeWiring().build()).checkTypeDirectives(errors)

        then:
        errors.each { assert it instanceof DirectiveUnknownArgumentError }
        errors.size() == 12
    }

    def "find illegal location directives"() {

        def spec = '''

            directive @testDirective on SCHEMA

            type ObjectType @testDirective {
                field(arg1 : String @testDirective) : String @testDirective
            }
            
            interface InterfaceType @testDirective {
                field(arg1 : String @testDirective) : String @testDirective
            }

            union UnionType @testDirective =  Foo | Bar
            
            enum EnumType @testDirective {
                val1 @testDirective
            }
            
            scalar ScalarType @testDirective
            
            input InputType @testDirective {
                field : String @testDirective
            }
            

        '''
        def registry = parse(spec)
        def errors = []

        when:
        new SchemaTypeDirectivesChecker(registry, RuntimeWiring.newRuntimeWiring().build()).checkTypeDirectives(errors)

        then:
        errors.each { assert it instanceof DirectiveIllegalLocationError }
        errors.size() == 12
    }

    def "catches directives that fail to provide non null arguments"() {
        def spec = '''

            directive @testDirective1(nonNullArg : String!) on FIELD_DEFINITION
            
            directive @testDirective2(nonNullArg : String! = "default") on FIELD_DEFINITION
            
            directive @testDirective3(nonNullArg : String) on FIELD_DEFINITION

            type Query {
                f1 : String @testDirective1
                f2 : String @testDirective1(nonNullArg : "someValue")
                f3 : String @testDirective2
                f4 : String @testDirective3
            }

        '''
        def registry = parse(spec)
        def errors = []

        when:
        new SchemaTypeDirectivesChecker(registry, RuntimeWiring.newRuntimeWiring().build()).checkTypeDirectives(errors)

        then:
        errors.size() == 1
        errors.each { assert it instanceof DirectiveMissingNonNullArgumentError }
    }

    def "directive must not reference itself"() {
        given:
        def spec = '''
            directive @invalidExample(arg: String @invalidExample) on ARGUMENT_DEFINITION
            
            type Query {
                f1 : String
            }
        '''
        def registry = parse(spec)
        def errors = []

        when:
        new SchemaTypeDirectivesChecker(registry, RuntimeWiring.newRuntimeWiring().build()).checkTypeDirectives(errors)

        then:
        errors.size() == 1
        errors.get(0) instanceof DirectiveIllegalReferenceError
        errors.get(0).getMessage() == "'invalidExample' must not reference itself on 'arg''[@2:39]'"
    }

    def "directive must not begin with '__'"() {
        given:
        def spec = '''
            directive @__invalidExample on ARGUMENT_DEFINITION
            
            type Query {
                f1 : String
            }
        '''
        def registry = parse(spec)
        def errors = []

        when:
        new SchemaTypeDirectivesChecker(registry, RuntimeWiring.newRuntimeWiring().build()).checkTypeDirectives(errors)

        then:
        errors.size() == 1
        errors.get(0) instanceof IllegalNameError
        errors.get(0).getMessage() == "'__invalidExample''[@2:13]' must not begin with '__', which is reserved by GraphQL introspection."
    }

    def "arguments of directive must not have a name which begins with the characters '__' "() {
        given:
        def spec = '''
            directive @directiveExample(__arg: String) on ARGUMENT_DEFINITION
            
            type Query {
                f1 : String
            }
        '''
        def registry = parse(spec)
        def errors = []

        when:
        new SchemaTypeDirectivesChecker(registry, RuntimeWiring.newRuntimeWiring().build()).checkTypeDirectives(errors)

        then:
        errors.size() == 1
        errors.get(0) instanceof IllegalNameError
        errors.get(0).getMessage() == "'__arg''[@2:41]' must not begin with '__', which is reserved by GraphQL introspection."
    }

    def "arguments of directive is not an input type"() {
        given:
        def spec = '''
            type NotInputType{
                field: String
            }
                
            directive @directiveExample(arg: NotInputType) on ARGUMENT_DEFINITION
            
            type Query {
                f1 : String
            }
        '''
        def registry = parse(spec)
        def errors = []

        when:
        new SchemaTypeDirectivesChecker(registry, RuntimeWiring.newRuntimeWiring().build()).checkTypeDirectives(errors)

        then:
        errors.size() == 1
        errors.get(0) instanceof NotAnInputTypeError
        errors.get(0).getMessage() == "The type 'NotInputType' [@2:13] is not an input type, but was used as an input type [@6:46]"
    }

    def "uses runtime wiring factory for scalars"() {
        given:
        def spec = '''
            directive @testDirective(knownArg : ScalarType!) on OBJECT

            scalar ScalarType

            type ObjectType @testDirective(knownArg : "x") {
                field : String
            }
        '''
        def registry = parse(spec)
        def scalarType = GraphQLScalarType
                .newScalar(Scalars.GraphQLString)
                .name("ScalarType")
                .build()
        def runtimeWiring = RuntimeWiring
                .newRuntimeWiring()
                .wiringFactory(new WiringFactory() {
                    @Override
                    boolean providesScalar(ScalarWiringEnvironment environment) {
                        return environment.scalarTypeDefinition.name == scalarType.name
                    }

                    @Override
                    GraphQLScalarType getScalar(ScalarWiringEnvironment environment) {
                        return scalarType
                    }
                })
                .build()
        def errors = []

        when:
        new SchemaTypeDirectivesChecker(registry, runtimeWiring).checkTypeDirectives(errors)

        then:
        errors.size() == 0
    }
}
