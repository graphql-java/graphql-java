package graphql.validation

import graphql.parser.Parser
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class KnownFragmentNamesTest extends Specification {

    def "unknown fragment reference in fragment spread"() {
        def query = """
            {
              dog {
                ...unknownFragment
              }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.any { it.validationErrorType == ValidationErrorType.UndefinedFragment }
    }

    def '5.4.2.1 Fragment spread target defined '() {
        def query = """
            query getDogName {
              dog {
                ... FragmentDoesNotExist
              }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.UndefinedFragment
        validationErrors.get(0).message == "Validation error (UndefinedFragment@[dog]) : Undefined fragment 'FragmentDoesNotExist'"
    }

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}
