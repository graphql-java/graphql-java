package graphql

import graphql.execution.ValuesResolver
import graphql.schema.GraphQLArgument
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.errors.SchemaProblem
import spock.lang.Specification

class Issue2001 extends Specification {

    def "test non-list value for a list argument of a directive"() {
        def spec = '''
            directive @test(value: [String] = "default") on FIELD_DEFINITION
            type Query {
                testDefaultWorks : String @test
                testItWorks : String @test(value: "test")
                testItIsNotBroken : String @test(value: ["test"])
            }
            '''

        def closure = {
            def argument = it.fieldDefinition.getDirective("test").getArgument("value") as GraphQLArgument
            return ValuesResolver.valueToInternalValue(argument.getArgumentValue(), argument.getType())[0]
        }
        def graphql = TestUtil.graphQL(spec, RuntimeWiring.newRuntimeWiring()
                    .type("Query", {
                        it.dataFetcher("testDefaultWorks", closure)
                                .dataFetcher("testItWorks", closure)
                                .dataFetcher("testItIsNotBroken", closure)
                    }).build())
                .build()

        when:
        def result = graphql.execute(' { testDefaultWorks testItWorks testItIsNotBroken }')

        then:
        result.errors.isEmpty()
        result.data.testDefaultWorks == "default"
        result.data.testItWorks == "test"
        result.data.testItIsNotBroken == "test"
    }
    def "test an incorrect non-list value for a list argument of a directive"() {
        def spec = '''
            directive @test(value: [String]) on FIELD_DEFINITION
            type Query {
                test : String @test(value : 123)
            }
            '''


        when:
        def reader = new StringReader(spec)
        def registry = new SchemaParser().parse(reader)

        def options = SchemaGenerator.Options.defaultOptions()

        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, TestUtil.mockRuntimeWiring)

        then:
        thrown(SchemaProblem.class)
    }
}
