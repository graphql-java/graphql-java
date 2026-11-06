package graphql

import spock.lang.Specification

class SkipAndInclude extends Specification {

    private def graphQL = TestUtil.graphQL("""
            type Query {
                field: Int
            }
        """).build()

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

    def "null value for the #directive if argument is a coerced null error, not a NullPointerException"() {
        given:
        // a nullable variable with a non null default value is a legal usage in the non null 'if' position,
        // so an explicit null variable value reaches the directive and must be reported as a coercion error
        def executionInput = ExecutionInput.newExecutionInput()
                .query("""
                    query(\$flag: Boolean = true) {
                        field @$directive(if: \$flag)
                    }
                    """)
                .variables([flag: null])
                .build()

        when:
        def executionResult = graphQL.execute(executionInput)

        then:
        executionResult.data == null
        executionResult.errors.size() == 1
        executionResult.errors[0].message.contains("coerced Null value for the non-null 'if' argument of directive '@$directive'")

        where:
        directive << ["skip", "include"]
    }
}
