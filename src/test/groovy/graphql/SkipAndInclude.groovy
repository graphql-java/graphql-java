package graphql

import spock.lang.Specification

class SkipAndInclude extends Specification {

    def "@skip and @include"() {
        when:
        def schema = TestUtil.schema("""
            type Query {
                field: Int
            }
        """)

        def graphQL = GraphQL.newGraphQL(schema).build()

        def executionInput = ExecutionInput.newExecutionInput()
                .query('''
                    query QueryWithSkipAndInclude($skip: Boolean!, $include: Boolean!) {
                        field @skip(if: $skip) @include(if: $include)
                    }   
                    ''')
                .variables([skip: skip, include: include])
                .build()

        def executionResult = graphQL.execute(executionInput)

        then:
        ((Map) executionResult.data).containsKey("field") == quaried

        where:
        skip    | include | quaried
        true    | true    | false
        true    | false   | false
        false   | true    | true
        false   | false   | false
    }
}
