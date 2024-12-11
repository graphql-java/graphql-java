package graphql.execution

import graphql.ExecutionInput
import graphql.TestUtil
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.GraphQL
import spock.lang.Specification

class NoErrorPropagationTest extends Specification {

    def "when error propagation is disabled null is returned"() {

        def sdl = '''
            type Query {
                foo : Int! 
            }
        '''

        def options = SchemaGenerator.Options.defaultOptions().addOnErrorDirective(true)

        def schema = TestUtil.schema(options, sdl, RuntimeWiring.MOCKED_WIRING)
        def graphql = GraphQL.newGraphQL(schema).build()

        def query = '''
            query GetFoo @errorHandling(onError: ALLOW_NULL) { foo }
        '''
        when:

        ExecutionInput ei = ExecutionInput.newExecutionInput(query).root(
                [foo: null]
        ).build()

        def er = graphql.execute(ei)

        then:
        er.data != null
        er.data.foo == null
        er.errors[0].path.toList() == ["foo"]
    }
}
