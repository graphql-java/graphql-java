package graphql.validation.rules

import graphql.parser.Parser
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class ProvidedNonNullArgumentsTest extends Specification {

    def "not provided non null field argument results in error"() {
        def query = """
            query getDogName {
              dog {
                doesKnowCommand
              }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.any { it.validationErrorType == ValidationErrorType.MissingFieldArgument }
    }

    def "not provided and not defaulted non null field argument with error message"() {
        def query = """
            query getDogName {
              dog {
                  doesKnowCommand
              }
            }
        """.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.MissingFieldArgument
        validationErrors[0].message == "Validation error (MissingFieldArgument@[dog/doesKnowCommand]) : Missing field argument 'dogCommand'"
    }

    def "all field arguments are provided results in no error"() {
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

    def "not provided not defaulted directive argument results in error"() {
        def query = """
            query getDogName {
              dog @nonNullDirective {
                name
              }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.any { it.validationErrorType == ValidationErrorType.MissingDirectiveArgument }
    }

    def "not provided and not defaulted non null directive argument with error message"() {
        def query = """
            query getDogName {
              dog @nonNullDirective {
                  name
              }
            }
        """.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.MissingDirectiveArgument
        validationErrors[0].message == "Validation error (MissingDirectiveArgument@[dog]) : Missing directive argument 'arg1'"
    }

    def "all directive arguments are provided results in no error"() {
        def query = """
            query getDogName {
              dog @nonNullDirective(arg1: "value") {
                name
              }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.empty
    }

    def "provide the explicit value null is not valid for non null argument"() {
        def query = """
            query getDogName {
              dog {
                doesKnowCommand(dogCommand: null)
              }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.any { it.validationErrorType == ValidationErrorType.NullValueForNonNullArgument }
    }

    def "provide the explicit value null is not valid for non null argument with error message"() {
        def query = """
            query getDogName {
              dog {
                  doesKnowCommand(dogCommand: null)
              }
            }
        """.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 2
        validationErrors[0].validationErrorType == ValidationErrorType.NullValueForNonNullArgument
        validationErrors[0].message == "Validation error (NullValueForNonNullArgument@[dog/doesKnowCommand]) : Null value for non-null field argument 'dogCommand'"
    }

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}
