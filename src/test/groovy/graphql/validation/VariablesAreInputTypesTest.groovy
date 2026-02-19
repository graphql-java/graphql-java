package graphql.validation

import graphql.TestUtil
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class VariablesAreInputTypesTest extends Specification {

    def "the unmodified ast type is not a schema input type"() {
        setup:
        def schema = '''
            type Droid {
                name: String
            }

            type Query {
                droid(id: String): Droid
            }
        '''

        def query = '''
            query(\$var: [Droid]!) {
                droid(id: "1") {
                    name
                }
            }
        '''

        def graphQlSchema = TestUtil.schema(schema)
        def document = TestUtil.parseQuery(query)
        def validator = new Validator()

        when:
        def validationErrors = validator.validateDocument(graphQlSchema, document, Locale.ENGLISH)

        then:
        !validationErrors.empty
        validationErrors.any { it.validationErrorType == ValidationErrorType.NonInputTypeOnVariable }
    }

    def "when a variable is of type GraphQLObjectType then it should not throw ClassCastException and validate with errors"() {
        setup:
        def schema = '''
            type User {
                id: String
            }

            input UserInput {
                id: String
            }

            type Mutation {
                createUser(user: UserInput): User
            }

            type Query {
                getUser: User
            }
        '''

        def query = '''
            mutation createUser($user: User){
                createUser(user: $user) {
                    id
                }
            }
        '''

        def graphQlSchema = TestUtil.schema(schema)
        def document = TestUtil.parseQuery(query)
        def validator = new Validator()

        when:
        def validationErrors = validator.validateDocument(graphQlSchema, document, Locale.ENGLISH)

        then:
        !validationErrors.empty
        validationErrors.size() == 2
        validationErrors.validationErrorType as Set ==
                [ValidationErrorType.VariableTypeMismatch, ValidationErrorType.NonInputTypeOnVariable] as Set
        validationErrors[0].message == "Validation error (NonInputTypeOnVariable) : Input variable 'user' type 'User' is not an input type"
    }
}
