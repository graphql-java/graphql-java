package graphql

import graphql.execution.NonNullableValueCoercedAsNullException
import spock.lang.Specification

class Issue743 extends Specification {

    def "#743 missing variables"() {
        when:
        def schema = TestUtil.schema("""
            type Query {
                version : Int
            }
        """)

        def graphQL = GraphQL.newGraphQL(schema).build()

        def executionInput = ExecutionInput.newExecutionInput()
                .query('''
                    query NPEDueToMissingVariable($isTrue: Boolean!) {
                        version @include(if: $isTrue)
                    }   
                    ''')
                .variables([:])
                .build()

        graphQL.execute(executionInput)

        then:

        thrown(NonNullableValueCoercedAsNullException)
    }
}
