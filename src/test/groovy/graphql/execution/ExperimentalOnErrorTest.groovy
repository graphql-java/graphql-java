package graphql.execution

import graphql.Directives
import graphql.ExecutionInput
import graphql.TestUtil
import spock.lang.Specification

class ExperimentalOnErrorTest extends Specification {

    void setup() {
        Execution.setExperimentalOnErrorEnabled(true)
    }

    def "with onError: NULL, null is returned"() {

        def sdl = '''
            type Query {
                foo : Int! 
            }
        '''

        def graphql = TestUtil.graphQL(sdl).build()

        def query = '''
            query GetFoo { foo }
        '''
        when:

        ExecutionInput ei = ExecutionInput.newExecutionInput(query).root(
                [foo: null]
        ).onError(OnError.NULL).build()

        def er = graphql.execute(ei)

        then:
        er.data != null
        er.data.foo == null
        er.errors[0].path.toList() == ["foo"]
    }

    def "with onError: HALT, execution stops and a request error is returned"() {

        def sdl = '''
            type Query {
                foo : Int!
                bar : Int
            }
        '''

        def graphql = TestUtil.graphQL(sdl).build()

        def query = '''
            query GetFoo { foo bar }
        '''
        when:

        ExecutionInput ei = ExecutionInput.newExecutionInput(query).root(
                [foo: null, bar: 42]
        ).onError(OnError.HALT).build()

        def er = graphql.execute(ei)

        then:
        er.data == null
        er.errors.size() == 1
        er.errors[0].path.toList() == ["foo"]
    }

    def "with onError: PROPAGATE, error is propagated"() {

        def sdl = '''
            type Query {
                foo : Int! 
            }
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

    def "With onError JVM disabled, error is propagated"() {
        def sdl = '''
            type Query {
                foo : Int! 
            }
        '''

        def graphql = TestUtil.graphQL(sdl).build()

        def query = '''
            query GetFoo { foo }
        '''
        when:

        Execution.setExperimentalOnErrorEnabled(false) // JVM wide

        ExecutionInput ei = ExecutionInput.newExecutionInput(query).root(
                [foo: null]
        ).onError(OnError.NULL).build()

        def er = graphql.execute(ei)

        then:
        er.data == null
        er.errors[0].message == "The field at path '/foo' was declared as a non null type, but the code involved in retrieving data has wrongly returned a null value.  The graphql specification requires that the parent field be set to null, or if that is non nullable that it bubble up null to its parent and so on. The non-nullable type is 'Int' within parent type 'Query'"
        er.errors[0].path.toList() == ["foo"]
    }
}
