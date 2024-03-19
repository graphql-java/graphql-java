package graphql.introspection

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.TestUtil
import spock.lang.Specification

class GoodFaithIntrospectionInstrumentationTest extends Specification {

    def graphql = TestUtil.graphQL("type Query { normalField : String }").build()

    def setup() {
        GoodFaithIntrospection.enabledJvmWide(true)
    }
    def cleanup() {
        GoodFaithIntrospection.enabledJvmWide(true)
    }

    def "test asking for introspection in good faith"() {

        when:
        ExecutionResult er = graphql.execute(IntrospectionQuery.INTROSPECTION_QUERY)
        then:
        er.errors.isEmpty()
    }

    def "test asking for introspection in bad faith"() {

        when:
        ExecutionResult er = graphql.execute(query)
        then:
        !er.errors.isEmpty()
        er.errors[0] instanceof GoodFaithIntrospection.BadFaithIntrospectionError

        where:
        query                                                                                                    | _
        // long attack
        """
        query badActor{__schema{types{fields{type{fields{type{fields{type{fields{type{name}}}}}}}}}}}
        """                                                                                           | _
        // a case for __Type interfaces
        """ query badActor {
                __schema { types { interfaces { fields { type { interfaces { name } } } } } }
            }
        """                                                                                           | _
        // a case for __Type inputFields
        """ query badActor {
                __schema { types { inputFields { type { inputFields { name }}}}}
            }
        """                                                                                           | _
        // a case for __Type possibleTypes
        """ query badActor {
                __schema { types { inputFields { type { inputFields { name }}}}}
            }
        """                                                                                           | _
        // a case leading from __InputValue
        """ query badActor {
                __schema { types { fields { args { type { name fields { name }}}}}}
            }
        """                                                                                           | _
        // a case leading from __Field
        """ query badActor {
                __schema { types { fields { type { name fields { name }}}}}
            }
        """                                                                                           | _
        // a case for __type
        """ query badActor {
                __type(name : "t") { name }
                alias1 :  __type(name : "t1") { name }
            }
        """                                                                                           | _
        // a case for schema repeated - dont ask twice
        """ query badActor {
                __schema { types { name} }
                alias1 : __schema { types { name} }
            }
        """                                                                                           | _
    }

    def "mixed general queries and introspections will be stopped anyway"() {
        def query = """
            query goodAndBad {
                normalField
                __schema{types{fields{type{fields{type{fields{type{fields{type{name}}}}}}}}}}
            }
        """

        when:
        ExecutionResult er = graphql.execute(query)
        then:
        !er.errors.isEmpty()
        er.errors[0] instanceof GoodFaithIntrospection.BadFaithIntrospectionError
        er.data == null // it stopped hard - it did not continue to normal business
    }

    def "can be disabled"() {
        when:
        def currentState = GoodFaithIntrospection.isEnabledJvmWide()

        then:
        currentState

        when:
        def prevState = GoodFaithIntrospection.enabledJvmWide(false)

        then:
        prevState

        when:
        ExecutionResult er = graphql.execute("query badActor{__schema{types{fields{type{fields{type{fields{type{fields{type{name}}}}}}}}}}}")

        then:
        er.errors.isEmpty()
    }

    def "can be disabled per request"() {
        when:
        def context = [(GoodFaithIntrospection.GOOD_FAITH_INTROSPECTION_DISABLED): true]
        ExecutionInput executionInput = ExecutionInput.newExecutionInput("query badActor{__schema{types{fields{type{fields{type{fields{type{fields{type{name}}}}}}}}}}}")
                .graphQLContext(context).build()
        ExecutionResult er = graphql.execute(executionInput)

        then:
        er.errors.isEmpty()

        when:
        context = [(GoodFaithIntrospection.GOOD_FAITH_INTROSPECTION_DISABLED): false]
        executionInput = ExecutionInput.newExecutionInput("query badActor{__schema{types{fields{type{fields{type{fields{type{fields{type{name}}}}}}}}}}}")
                .graphQLContext(context).build()
        er = graphql.execute(executionInput)

        then:
        !er.errors.isEmpty()
        er.errors[0] instanceof GoodFaithIntrospection.BadFaithIntrospectionError
    }
}
