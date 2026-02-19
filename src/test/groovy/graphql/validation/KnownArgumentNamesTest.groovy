package graphql.validation

import graphql.parser.Parser
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class KnownArgumentNamesTest extends Specification {

    def "unknown field argument"() {
        def query = """
            query getDog {
              dog {
                doesKnowCommand(dogCommand: SIT, unknownArg: false)
              }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.any { it.validationErrorType == ValidationErrorType.UnknownArgument }
    }

    def "known field argument"() {
        def query = """
            query getDog {
              dog {
                doesKnowCommand(dogCommand: SIT)
              }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.empty
    }

    def "unknown directive argument"() {
        def query = """
            query getDogName {
              dog @dogDirective(unknownArg: "value") {
                name
              }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.any { it.validationErrorType == ValidationErrorType.UnknownDirective }
    }

    def "known directive argument results in no error"() {
        def query = """
            query getDogName {
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

    def "directive missing argument validation error with message"() {
        def query = """
            query getDogName {
              dog @dogDirective(notArgument: "value"){
                  name
              }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.UnknownDirective
        validationErrors.get(0).message == "Validation error (UnknownDirective@[dog]) : Unknown directive argument 'notArgument'"
    }

    def "field missing argument validation error with message"() {
        def query = """
            query getDog {
              dog {
                  doesKnowCommand(dogCommand: SIT, notArgument: false)
              }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.UnknownArgument
        validationErrors.get(0).message == "Validation error (UnknownArgument@[dog/doesKnowCommand]) : Unknown field argument 'notArgument'"
    }

    def "directive argument not validated against field arguments"() {
        def query = """
            query getDog {
              dog {
                doesKnowCommand(dogCommand: SIT) @dogDirective(dogCommand: SIT)
              }
            }
        """
        when:
        def validationErrors = validate(query)
        then:
        validationErrors.any { it.validationErrorType == ValidationErrorType.UnknownDirective }
    }

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}
