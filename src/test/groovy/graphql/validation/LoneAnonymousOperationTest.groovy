package graphql.validation

import graphql.parser.Parser
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class LoneAnonymousOperationTest extends Specification {
    def '5.1.2.1 Lone Anonymous Operation Valid'() {
        def query = """
            {
              dog {
                name
              }
            }
            """
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.empty
    }


    def '5.1.2.1 Lone Anonymous Operation Not Valid'() {
        def query = """
            {
              dog {
                name
              }
            }

            query getName {
              dog {
                owner {
                  name
                }
              }
            }
            """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors[0].validationErrorType == ValidationErrorType.LoneAnonymousOperationViolation
        validationErrors[0].message == "Validation error (LoneAnonymousOperationViolation) : Operation 'getName' is following anonymous operation"
    }

    def '5.1.2.1 Lone Anonymous Operation Not Valid (reverse order) '() {
        def query = """
            query getName {
              dog {
                owner {
                  name
                }
              }
            }

            {
              dog {
                name
              }
            }
            """

        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors[0].validationErrorType == ValidationErrorType.LoneAnonymousOperationViolation
        validationErrors[0].message == "Validation error (LoneAnonymousOperationViolation) : Anonymous operation with other operations"
    }

    def '5.1.2.1 Lone Anonymous Operation Not Valid (not really alone)'() {
        def query = """
            {
              dog {
                owner {
                  name
                }
              }
            }
              
            {
              dog {
                name
              }
            }
            """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors[0].validationErrorType == ValidationErrorType.LoneAnonymousOperationViolation
        validationErrors[0].message == "Validation error (LoneAnonymousOperationViolation) : Anonymous operation with other operations"
    }

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}
