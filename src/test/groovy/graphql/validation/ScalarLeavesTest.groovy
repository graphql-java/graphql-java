package graphql.validation

import graphql.parser.Parser
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class ScalarLeavesTest extends Specification {

    def "subselection not allowed"() {
        def query = """
            {
              dog {
                name {
                  something
                }
              }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.any { it.validationErrorType == ValidationErrorType.SubselectionNotAllowed }
    }

    def "subselection not allowed with error message"() {
        def query = """
        query dogOperation {
            dog {
                name {
                    id
                }
            }
        }
        """.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.SubselectionNotAllowed
        validationErrors[0].message == "Validation error (SubselectionNotAllowed@[dog/name]) : Subselection not allowed on leaf type 'String!' of field 'name'"
    }

    def "subselection required"() {
        def query = """
            {
              dog
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.any { it.validationErrorType == ValidationErrorType.SubselectionRequired }
    }

    def "subselection required with error message"() {
        def query = """
        query dogOperation {
            dog
        }
        """.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.SubselectionRequired
        validationErrors[0].message == "Validation error (SubselectionRequired@[dog]) : Subselection required for type 'Dog' of field 'dog'"
    }

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}
