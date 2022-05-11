package graphql

import spock.lang.Specification

class Issue743 extends Specification {

    def "#743 missing variables"() {
        when:
        def graphQL = TestUtil.graphQL("""
            type Query {
                version : Int
            }
        """).build()

        def executionInput = ExecutionInput.newExecutionInput()
                .query('''
                    query NPEDueToMissingVariable($isTrue: Boolean!) {
                        version @include(if: $isTrue)
                    }   
                    ''')
                .variables([:])
                .build()

        def executionResult = graphQL.execute(executionInput)

        then:

        executionResult.data == null
        executionResult.errors.size() == 1
        executionResult.errors[0].errorType == ErrorType.ValidationError
        executionResult.errors[0].message == "Variable 'isTrue' has an invalid value: Variable 'isTrue' has coerced Null value for NonNull type 'Boolean!'"
    }
}
