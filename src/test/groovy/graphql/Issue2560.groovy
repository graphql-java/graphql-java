package graphql

import spock.lang.Specification

class Issue2560 extends Specification {
    def "invalid enum error message is nested and source location missing"() {
        when:
        def mutation = '''
            mutation UpdatePerson($input: PersonInput!) {
                updatePerson(input: $input)
            }
        '''

        def graphQL = TestUtil.graphQL('''
            enum PositionType {
                MANAGER
                DEVELOPER
            }
            
            input PersonInput {
                name: String
                position: PositionType
            }

            type Query {
                name: String
            }
            
            type Mutation {
              updatePerson(input: PersonInput!): Boolean
              updateLol(input: String!): Boolean
            }
        ''').build()

        def executionInput = ExecutionInput.newExecutionInput()
                .query(mutation)
                .variables([input: [name: 'Name', position: 'UNKNOWN_POSITION'] ])
                .build()


        def executionResult = graphQL.execute(executionInput)

        then:
        executionResult.data == null
        executionResult.errors.size() == 1
        executionResult.errors[0].errorType == ErrorType.ValidationError
        executionResult.errors[0].message == 'invalid value : invalid value : invalid value : Invalid input for Enum \'PositionType\'. No value found for name \'UNKNOWN_POSITION\''
    }
}
