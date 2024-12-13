package graphql.execution

import graphql.ExecutionInput
import graphql.ExperimentalApi
import graphql.TestUtil
import spock.lang.Specification

class NoErrorPropagationTest extends Specification {

    def "when onError is ALLOW_NULL null is returned"() {

        def sdl = '''
            type Query {
                foo : Int! 
            }
            enum OnError {
              ALLOW_NULL
              PROPAGATE
            }
            directive @errorHandling(onError: OnError) on QUERY | MUTATION | SUBSCRIPTION
        '''

        def graphql = TestUtil.graphQL(sdl).build()

        def query = '''
            query GetFoo @errorHandling(onError: ALLOW_NULL) { foo }
        '''
        when:

        ExecutionInput ei = ExecutionInput.newExecutionInput(query).root(
                [foo: null]
        ).graphQLContext([(ExperimentalApi.ENABLE_CUSTOM_ERROR_HANDLING): true])
                .build()

        def er = graphql.execute(ei)

        then:
        er.data != null
        er.data.foo == null
        er.errors[0].path.toList() == ["foo"]
    }

    def "when onError is PROPAGATE error is propagated"() {

        def sdl = '''
            type Query {
                foo : Int! 
            }
            enum OnError {
              ALLOW_NULL
              PROPAGATE
            }
            directive @errorHandling(onError: OnError) on QUERY | MUTATION | SUBSCRIPTION
        '''

        def graphql = TestUtil.graphQL(sdl).build()

        def query = '''
            query GetFoo @errorHandling(onError: PROPAGATE) { foo }
        '''
        when:

        ExecutionInput ei = ExecutionInput.newExecutionInput(query).root(
                [foo: null]
        ).graphQLContext([(ExperimentalApi.ENABLE_CUSTOM_ERROR_HANDLING): true])
                .build()

        def er = graphql.execute(ei)

        then:
        er.data == null
        er.errors[0].path.toList() == ["foo"]
    }


    def "when custom error propagation is disabled error is propagated"() {

        def sdl = '''
            type Query {
                foo : Int! 
            }
            enum OnError {
              ALLOW_NULL
              PROPAGATE
            }
            directive @errorHandling(onError: OnError) on QUERY | MUTATION | SUBSCRIPTION
        '''

        def graphql = TestUtil.graphQL(sdl).build()

        def query = '''
            query GetFoo @errorHandling(onError: ALLOW_NULL) { foo }
        '''
        when:

        ExecutionInput ei = ExecutionInput.newExecutionInput(query).root(
                [foo: null]
        ).build()

        def er = graphql.execute(ei)

        then:
        er.data == null
        er.errors[0].path.toList() == ["foo"]
    }

    def "when @errorHandling is not added to the schema operation does not validate"() {

        def sdl = '''
            type Query {
                foo : Int! 
            }
        '''

        def graphql = TestUtil.graphQL(sdl).build()

        def query = '''
            query GetFoo @errorHandling(onError: PROPAGATE) { foo }
        '''
        when:

        ExecutionInput ei = ExecutionInput.newExecutionInput(query).root(
                [foo: null]
        ).graphQLContext([(ExperimentalApi.ENABLE_CUSTOM_ERROR_HANDLING): true])
                .build()

        def er = graphql.execute(ei)

        then:
        er.data == null
        er.errors[0].message.equals("Validation error (UnknownDirective) : Unknown directive 'errorHandling'")
    }

}
