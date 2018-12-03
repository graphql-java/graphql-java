package graphql.execution.conversion

import graphql.GraphQL
import graphql.TestUtil
import graphql.schema.DataFetcher
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLInputObjectType
import spock.lang.Specification

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class ArgumentConversionsTest extends Specification {

    static GraphQLInputObjectType inputType(String typeName) {
        GraphQLInputObjectType.newInputObject().name(typeName).build()
    }

    static GraphQLArgument arg(String typeName) {
        def inputObjectType = inputType(typeName)
        GraphQLArgument.newArgument().name("arg").type(inputObjectType).build()
    }

    static ArgumentConverterEnvironmentImpl env(String typeName) {
        def argument = arg(typeName)
        ArgumentConverterEnvironmentImpl.newEnvironment().argument(argument).sourceObject("SourceX").build()
    }

    def "can compose a series of conversions"() {


        def conversions = ArgumentConversions.newConversions()
                .converter("typeA", { env -> "convertedValueA" })
                .converters([
                typeB: { env -> "convertedValueB" } as ArgumentConverter,
                typeC: { env -> "convertedValueC" } as ArgumentConverter])
                .converter(inputType("typeD"), { env -> "convertedValueD" })
                .build()
        when:
        def actual = conversions.convertArgument(env(actualTypeName))

        then:
        actual == expected

        where:
        actualTypeName || expected
        "typeA"        || "convertedValueA"
        "typeB"        || "convertedValueB"
        "typeC"        || "convertedValueC"
        "typeD"        || "convertedValueD"
        "unknownType"  || "SourceX"
    }

    class Person {
        def name
        def age

        Person(name, age) {
            this.name = name
            this.age = age
        }
    }

    def "integration test of values conversion"() {

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
        def runtimeWiring = newRuntimeWiring().type(newTypeWiring("Query").dataFetcher("person", df)).build()
        def schema = TestUtil.schema(spec, runtimeWiring)

        // TODO - remove this with the ability to inject the code registry during schema generation

        GraphQLCodeRegistry codeRegistry = schema.getCodeRegistry().transform({ codeReg ->
            codeReg.argumentConverters(ArgumentConversions.newConversions().converter("PersonInput", { env ->
                new Person(
                        env.sourceObject["name"].toString().reverse(),
                        env.sourceObject["age"] * 10
                )
            }))
        })
        schema = schema.transform({ builder -> builder.codeRegistry(codeRegistry) })

        def graphql = GraphQL.newGraphQL(schema).build()

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
}
