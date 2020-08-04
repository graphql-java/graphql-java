package graphql

import graphql.language.ArrayValue
import graphql.schema.CoercingParseLiteralException
import graphql.schema.idl.RuntimeWiring
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
            return it.fieldDefinition
                    .getDirective("test")
                    .getArgument("value")
                    .value[0]
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
        def graphql = TestUtil.graphQL(spec)

        then:
        thrown(CoercingParseLiteralException.class)
    }
}
