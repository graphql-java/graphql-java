package graphql.validation

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.parser.Parser
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class FragmentsOnCompositeTypeTest extends Specification {

    def "inline fragment type condition must refer to a composite type"() {
        def query = """
            {
              dog {
                ... on String {
                  name
                }
              }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.any { it.validationErrorType == ValidationErrorType.InlineFragmentTypeConditionInvalid }
    }

    def "should result in no error for inline fragment without type condition"() {
        def query = """
            {
              dog {
                ... {
                  name
                }
              }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.empty
    }

    def "should result in no error for inline fragment with composite type condition"() {
        def query = """
            {
              dog {
                ... on Pet {
                  name
                }
              }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.empty
    }

    def "fragment type condition must refer to a composite type"() {
        def query = """
            {
              dog {
                ...frag
              }
            }
            fragment frag on String {
              length
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.any { it.validationErrorType == ValidationErrorType.FragmentTypeConditionInvalid }
    }

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
        (executionResult.errors[0] as ValidationError).message == "Validation error (FragmentTypeConditionInvalid@[fragOnInputType]) : Fragment type condition is invalid, must be on Object/Interface/Union"
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
        (executionResult.errors[0] as ValidationError).message == "Validation error (InlineFragmentTypeConditionInvalid@[updateUDI]) : Inline fragment type condition is invalid, must be on Object/Interface/Union"
    }

    def "unknown type on inline fragment should not trigger composite type error"() {
        def query = """
            {
              dog {
                ... on StrangeType {
                  __typename
                }
              }
            }
        """
        when:
        def validationErrors = validate(query)
        then:
        // Should have KnownTypeNames error, but NOT InlineFragmentTypeConditionInvalid
        !validationErrors.any { it.validationErrorType == ValidationErrorType.InlineFragmentTypeConditionInvalid }
    }

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}
