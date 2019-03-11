package graphql

import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class Issue1440 extends Specification {

    def schema = TestUtil.schema("""
            type Query {
                nothing: String
            }
            
            type Mutation {
                updateUDI(input: UDIInput!): UDIOutput
            }
            
            type UDIOutput {
                device: String
                version: String
            }
            
            input UDIInput {
                device: String 
                version: String
            }
        """)

    def graphQL = GraphQL.newGraphQL(schema).build()


    def "#1440 when fragment type condition is input type it should return validation error - not classCastException"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput()
                .query('''                    
                    mutation UpdateUDI($input: UDIInput!) { 
                        updateUDI(input: $input) { 
                            ...fragOnInputType 
                            __typename 
                        } 
                    }
                    
                    # fragment should only target composite types
                    fragment fragOnInputType on UDIInput { 
                        device
                        version 
                        __typename 
                    } 
                    
                    ''')
                .variables([input: [device: 'device', version: 'version'] ])
                .build()

        def executionResult = graphQL.execute(executionInput)

        then:

        executionResult.data == null
        executionResult.errors.size() == 1
        (executionResult.errors[0] as ValidationError).validationErrorType == ValidationErrorType.FragmentTypeConditionInvalid
    }

    def "#1440 when inline fragment type condition is input type it should return validation error - not classCastException"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput()
                .query('''                    
                    mutation UpdateUDI($input: UDIInput!) { 
                        updateUDI(input: $input) { 
                            # fragment should only target composite types
                            ... on UDIInput { 
                                device
                                version 
                                __typename 
                            }  
                            __typename 
                        } 
                    }
                    ''')
                .variables([input: [device: 'device', version: 'version'] ])
                .build()

        def executionResult = graphQL.execute(executionInput)

        then:

        executionResult.data == null
        executionResult.errors.size() == 1
        (executionResult.errors[0] as ValidationError).validationErrorType == ValidationErrorType.InlineFragmentTypeConditionInvalid
    }
}
