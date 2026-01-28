package graphql.validation

import graphql.TestUtil
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class VariableDefaultValuesOfCorrectTypeTest extends Specification {

    def "default value has wrong type"() {
        setup:
        def schema = '''
            type Query {
                field(arg: String) : String
            }
        '''

        def query = '''
            query($arg: String = false) {
                field(arg: $arg)
            }
        '''

        def graphQlSchema = TestUtil.schema(schema)
        def document = TestUtil.parseQuery(query)
        def validator = new Validator()

        when:
        def validationErrors = validator.validateDocument(graphQlSchema, document, Locale.ENGLISH)

        then:
        !validationErrors.empty
        validationErrors.any { it.validationErrorType == ValidationErrorType.BadValueForDefaultArg }
    }

    def "default value has wrong type with error message"() {
        setup:
        def schema = '''
            type User {
                id: String
            }

            type Query {
                getUsers(howMany: Int) : [User]
            }
        '''

        def query = '''
            query($howMany: Int = "NotANumber") {
                getUsers(howMany: $howMany) {
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
        validationErrors.size() == 1
        validationErrors[0].getValidationErrorType() == ValidationErrorType.BadValueForDefaultArg
        validationErrors[0].message == "Validation error (BadValueForDefaultArg) : Bad default value 'StringValue{value='NotANumber'}' for type 'Int'"
    }

    def "default value has wrong type with error message of client (German), not server (English)"() {
        setup:
        def schema = '''
            type User {
                id: String
            }

            type Query {
                getUsers(howMany: Int) : [User]
            }
        '''

        def query = '''
            query($howMany: Int = "NotANumber") {
                getUsers(howMany: $howMany) {
                    id
                }
            }
        '''

        def graphQlSchema = TestUtil.schema(schema)
        def document = TestUtil.parseQuery(query)
        def validator = new Validator()

        when:
        def validationErrors = validator.validateDocument(graphQlSchema, document, Locale.GERMAN)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].getValidationErrorType() == ValidationErrorType.BadValueForDefaultArg
        validationErrors[0].message == "Validierungsfehler (BadValueForDefaultArg) : Ungültiger Standardwert 'StringValue{value='NotANumber'}' für Typ 'Int'"
    }
}
