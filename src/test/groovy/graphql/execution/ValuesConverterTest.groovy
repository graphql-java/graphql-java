package graphql.execution


import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.conversion.ArgumentConverter
import graphql.schema.DataFetcher
import graphql.schema.GraphQLArgument
import spock.lang.Specification

import static graphql.ExecutionInput.newExecutionInput
import static graphql.Scalars.GraphQLInt
import static graphql.schema.FieldCoordinates.coordinates
import static graphql.schema.GraphQLCodeRegistry.newCodeRegistry
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring

class ValuesConverterTest extends Specification {

    class ConvertedValue {
        def convertedFrom
        def argument

        ConvertedValue(convertedFrom, argument) {
            this.convertedFrom = convertedFrom
            this.argument = argument
        }
    }

    def inputObjectType = newInputObject()
            .name("inputObject")
            .field(newInputObjectField()
            .name("intKey")
            .type(nonNull(GraphQLInt))
            .build())
            .build()
    def fieldArgument = new GraphQLArgument("arg", inputObjectType)

    def "converter gets called"() {
        given:
        ArgumentConverter converter = { env -> new ConvertedValue(env.getValueToBeConverted(), env.getArgument()) }
        def codeRegistry = newCodeRegistry().argumentConverter(converter).build()

        when:
        def value = new ValuesConverter().convertValue([intKey: 1], codeRegistry, fieldArgument)

        then:
        value instanceof ConvertedValue
        def convertedValue = value as ConvertedValue

        convertedValue.convertedFrom == [intKey: 1]
        convertedValue.argument.name == "arg"
        convertedValue.argument.type.name == "inputObject"
    }

    def "first converter wins"() {
        given:
        ArgumentConverter converter1 = { env -> new ConvertedValue(env.getValueToBeConverted(), env.getArgument()) }
        ArgumentConverter converter2 = { env -> throw new RuntimeException("Bang") }
        def codeRegistry = newCodeRegistry().argumentConverters(converter1, converter2).build()

        when:
        def value = new ValuesConverter().convertValue([intKey: 1], codeRegistry, fieldArgument)

        then:
        value instanceof ConvertedValue
    }

    def "all converters called until ones changes it"() {
        given:
        ArgumentConverter converter1 = { env -> env.valueToBeConverted }
        ArgumentConverter converter2 = { env -> new ConvertedValue("secondCalled", env.getArgument()) }
        ArgumentConverter converter3 = { env -> new ConvertedValue("thirdCalled", env.getArgument()) }
        def codeRegistry = newCodeRegistry().argumentConverters(converter1, converter2, converter3).build()

        when:
        def value = new ValuesConverter().convertValue([intKey: 1], codeRegistry, fieldArgument)

        then:
        value instanceof ConvertedValue
        def convertedValue = value as ConvertedValue
        convertedValue.convertedFrom == "secondCalled"
    }

    class Person {
        def name
        def age

        Person(name, age) {
            this.name = name
            this.age = age
        }
    }
    ArgumentConverter personArgumentConverter = { env ->
        new Person(
                env.valueToBeConverted["name"].toString().reverse(),
                env.valueToBeConverted["age"] * 10
        )
    }

    def spec = '''
            type Query {
                person(arg: PersonInput)  : Person
            }
            
            input PersonInput {
                name : String
                age : Int
            }
            
            type Person {
                name : String
                age : Int
            }
        '''


    DataFetcher df = { env -> env.getArgument("arg") }

    def runtimeWiring = newRuntimeWiring()
            .codeRegistry(newCodeRegistry()
            .argumentConverter(personArgumentConverter)
            .dataFetcher(coordinates("Query", "person"), df))
            .build()
    def schema = TestUtil.schema(spec, runtimeWiring)

    def graphql = GraphQL.newGraphQL(schema).build()

    def "integration test of values conversion"() {
        when:
        def executionResult = graphql.execute('''
        query { 
            person(arg : { name : "Brad", age : 49}) {
                name
                age
            }
        }
        ''')
        then:
        executionResult.errors.isEmpty()
        executionResult.data == [person: [name: "darB", age: 490]]
    }

    def "integration tests that variables will be converted"() {
        when:
        def executionResult = graphql.execute(newExecutionInput().query('''
        query($var : PersonInput ) { 
            person(arg : $var) {
                name
                age
            }
        }
        ''').variables(var: [name: "Brad", age: 49]))
        then:
        executionResult.errors.isEmpty()
        executionResult.data == [person: [name: "darB", age: 490]]
    }
}
