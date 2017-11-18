package graphql

import spock.lang.Specification

class SkipAndInclude extends Specification {

    private def graphQL = GraphQL.newGraphQL(TestUtil.schema("""
            type Query {
                field: Int
            }
        """)).build()

    def "@skip and @include"() {
        when:
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
        ((Map) executionResult.data).containsKey("field") == queried

        where:
        skip  | include | queried
        true  | true    | false
        true  | false   | false
        false | true    | true
        false | false   | false

    }

    def "@skip"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput()
                .query('''
                    query QueryWithSkip($skip: Boolean!) {
                        field @skip(if: $skip)
                    }   
                    ''')
                .variables([skip: skip])
                .build()

        def executionResult = graphQL.execute(executionInput)

        then:
        ((Map) executionResult.data).containsKey("field") == queried

        where:
        skip  | queried
        true  | false
        false | true
    }

    def "@include"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput()
                .query('''
                    query QueryWithInclude($include: Boolean!) {
                        field @include(if: $include)
                    }   
                    ''')
                .variables([include: include])
                .build()

        def executionResult = graphQL.execute(executionInput)

        then:
        ((Map) executionResult.data).containsKey("field") == queried

        where:
        include | queried
        true    | true
        false   | false
    }
}
