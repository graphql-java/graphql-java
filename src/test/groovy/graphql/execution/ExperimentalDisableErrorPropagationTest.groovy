package graphql.execution

import graphql.Directives
import graphql.ExecutionInput
import graphql.TestUtil
import spock.lang.Specification

class ExperimentalDisableErrorPropagationTest extends Specification {

    void setup() {
        Directives.setExperimentalDisableErrorPropagationEnabled(true)
    }

    def "with experimental_disableErrorPropagation, null is returned"() {

        def sdl = '''
            type Query {
                foo : Int! 
            }
            directive @experimental_disableErrorPropagation on QUERY | MUTATION | SUBSCRIPTION
        '''

        def graphql = TestUtil.graphQL(sdl).build()

        def query = '''
            query GetFoo @experimental_disableErrorPropagation { foo }
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

    def "without experimental_disableErrorPropagation, error is propagated"() {

        def sdl = '''
            type Query {
                foo : Int! 
            }
            directive @experimental_disableErrorPropagation on QUERY | MUTATION | SUBSCRIPTION
        '''

        def graphql = TestUtil.graphQL(sdl).build()

        def query = '''
            query GetFoo { foo }
        '''
        when:

        ExecutionInput ei = ExecutionInput.newExecutionInput(query).root(
                [foo: null]
        ).build()

        def er = graphql.execute(ei)

        then:
        er.data == null
        er.errors[0].message == "The field at path '/foo' was declared as a non null type, but the code involved in retrieving data has wrongly returned a null value.  The graphql specification requires that the parent field be set to null, or if that is non nullable that it bubble up null to its parent and so on. The non-nullable type is 'Int' within parent type 'Query'"
        er.errors[0].path.toList() == ["foo"]
    }

    def "With experimental_disableErrorPropagation JVM disabled, error is propagated"() {
        def sdl = '''
            type Query {
                foo : Int! 
            }
            directive @experimental_disableErrorPropagation on QUERY | MUTATION | SUBSCRIPTION
        '''

        def graphql = TestUtil.graphQL(sdl).build()

        def query = '''
            query GetFoo @experimental_disableErrorPropagation { foo }
        '''
        when:

        Directives.setExperimentalDisableErrorPropagationEnabled(false) // JVM wide

        ExecutionInput ei = ExecutionInput.newExecutionInput(query).root(
                [foo: null]
        ).build()

        def er = graphql.execute(ei)

        then:
        er.data == null
        er.errors[0].message == "The field at path '/foo' was declared as a non null type, but the code involved in retrieving data has wrongly returned a null value.  The graphql specification requires that the parent field be set to null, or if that is non nullable that it bubble up null to its parent and so on. The non-nullable type is 'Int' within parent type 'Query'"
        er.errors[0].path.toList() == ["foo"]
    }

    def "when @experimental_disableErrorPropagation is not added to the schema operation is gets added by schema code"() {

        def sdl = '''
            type Query {
                foo : Int! 
            }
        '''

        when:
        def graphql = TestUtil.graphQL(sdl).build()

        then:
        graphql.getGraphQLSchema().getDirective(Directives.ExperimentalDisableErrorPropagationDirective.getName()) === Directives.ExperimentalDisableErrorPropagationDirective
    }

}
