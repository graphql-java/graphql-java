package graphql.schema.idl

import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLEnumValueDefinition
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLUnionType
import spock.lang.Specification

import static graphql.TestUtil.schema

class SchemaGeneratorDirectiveBehaviourHelperTest extends Specification {

    def scalarType = new GraphQLScalarType("ScalarType", "", new Coercing() {
        @Override
        Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
            return null
        }

        @Override
        Object parseValue(Object input) throws CoercingParseValueException {
            return null
        }

        @Override
        Object parseLiteral(Object input) throws CoercingParseLiteralException {
            return null
        }
    })


    def "will trace down into each directive callback"() {

        def spec = '''
            type Query {
                f : ObjectType
                s : ScalarType
            }

            type ObjectType @objectDirective(target : "ObjectType") {
                field1 : String @fieldDirective(target : "field1")
                field2 : String @fieldDirective(target : "field2")
                field3(argument1 : String @argumentDirective(target : "argument1") argument2 : String @argumentDirective(target : "argument2")) : Int   
            }
            
            interface InterfaceType @interfaceDirective(target : "InterfaceType") {
                interfaceField1 : String @fieldDirective(target : "interfaceField1")
                interfaceField2 : String @fieldDirective(target : "interfaceField2")
            }
            
            type Foo {
                foo : Int
            }
            
            type Bar {
                bar :Int
            }
            
            union UnionType @unionDirective(target : "UnionType")  = Foo | Bar
            
            input InputType @inputDirective(target : "InputType") {
                inputField1 : String @inputFieldDirective(target : "inputField1")
                inputField2 : String @inputFieldDirective(target : "inputField2")
            }
            
            enum EnumType @enumDirective(target:"EnumType") {
                enumVal1 @enumValueDirective(target : "enumVal1")
                enumVal2 @enumValueDirective(target : "enumVal2")
            }
            
            scalar ScalarType @scalarDirective(target:"ScalarType")
            
        '''

        def targetList = []

        def schemaDirectiveWiring = new SchemaDirectiveWiring() {

            def assertDirectiveTarget(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment, String name) {
                targetList.add(name)
                GraphQLDirective directive = environment.getDirective()
                String target = directive.getArgument("target").getDefaultValue()
                assert name == target, " The target $target is not equal to the object name $name"
                return environment.getTypeElement()
            }

            @Override
            GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
                String name = environment.getTypeElement().getName()
                return assertDirectiveTarget(environment, name)
            }

            @Override
            GraphQLArgument onArgument(SchemaDirectiveWiringEnvironment<GraphQLArgument> environment) {
                String name = environment.getTypeElement().getName()
                return assertDirectiveTarget(environment, name)
            }

            @Override
            GraphQLObjectType onObject(SchemaDirectiveWiringEnvironment<GraphQLObjectType> environment) {
                String name = environment.getTypeElement().getName()
                return assertDirectiveTarget(environment, name)
            }

            @Override
            GraphQLInterfaceType onInterface(SchemaDirectiveWiringEnvironment<GraphQLInterfaceType> environment) {
                String name = environment.getTypeElement().getName()
                return assertDirectiveTarget(environment, name)
            }

            @Override
            GraphQLUnionType onUnion(SchemaDirectiveWiringEnvironment<GraphQLUnionType> environment) {
                String name = environment.getTypeElement().getName()
                return assertDirectiveTarget(environment, name)
            }

            @Override
            GraphQLEnumType onEnum(SchemaDirectiveWiringEnvironment<GraphQLEnumType> environment) {
                String name = environment.getTypeElement().getName()
                return assertDirectiveTarget(environment, name)
            }

            @Override
            GraphQLEnumValueDefinition onEnumValue(SchemaDirectiveWiringEnvironment<GraphQLEnumValueDefinition> environment) {
                String name = environment.getTypeElement().getName()
                return assertDirectiveTarget(environment, name)
            }

            @Override
            GraphQLScalarType onScalar(SchemaDirectiveWiringEnvironment<GraphQLScalarType> environment) {
                String name = environment.getTypeElement().getName()
                return assertDirectiveTarget(environment, name)
            }

            @Override
            GraphQLInputObjectType onInputObjectType(SchemaDirectiveWiringEnvironment<GraphQLInputObjectType> environment) {
                String name = environment.getTypeElement().getName()
                return assertDirectiveTarget(environment, name)
            }

            @Override
            GraphQLInputObjectField onInputObjectField(SchemaDirectiveWiringEnvironment<GraphQLInputObjectField> environment) {
                String name = environment.getTypeElement().getName()
                return assertDirectiveTarget(environment, name)
            }
        }
        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .directive("fieldDirective", schemaDirectiveWiring)
                .directive("argumentDirective", schemaDirectiveWiring)
                .directive("objectDirective", schemaDirectiveWiring)
                .directive("interfaceDirective", schemaDirectiveWiring)
                .directive("unionDirective", schemaDirectiveWiring)
                .directive("inputDirective", schemaDirectiveWiring)
                .directive("inputFieldDirective", schemaDirectiveWiring)
                .directive("enumDirective", schemaDirectiveWiring)
                .directive("enumValueDirective", schemaDirectiveWiring)
                .directive("scalarDirective", schemaDirectiveWiring)
                .scalar(scalarType)
                .wiringFactory(new MockedWiringFactory())
                .build()

        when:
        def schema = schema(spec, runtimeWiring)

        then:
        schema != null
        targetList.contains("ObjectType")
        targetList.contains("field1")
        targetList.contains("field2")

        targetList.contains("InterfaceType")
        targetList.contains("interfaceField1")
        targetList.contains("interfaceField2")

        targetList.contains("UnionType")

        targetList.contains("InputType")
        targetList.contains("inputField1")
        targetList.contains("inputField2")

        targetList.contains("EnumType")
        targetList.contains("enumVal1")
        targetList.contains("enumVal2")

        targetList.contains("ScalarType")
    }
}
