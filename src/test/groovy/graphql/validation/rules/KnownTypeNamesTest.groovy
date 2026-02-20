package graphql.validation.rules

import graphql.StarWarsSchema
import graphql.language.TypeName
import graphql.parser.Parser
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationContext
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class KnownTypeNamesTest extends Specification {

    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    ValidationContext validationContext = Mock(ValidationContext)
    KnownTypeNames knownTypeNames = new KnownTypeNames(validationContext, errorCollector)

    def setup() {
        validationContext.i18n(_, _) >> "test error message"
    }

    def "unknown types is an error"() {
        given:
        knownTypeNames.validationContext.getSchema() >> StarWarsSchema.starWarsSchema

        when:
        knownTypeNames.checkTypeName(TypeName.newTypeName("Simpson").build())

        then:
        errorCollector.containsValidationError(ValidationErrorType.UnknownType)
    }

    def '5.7.3 Variables Are Input Types - unknown type'() {
        def query = """
            query madDog(\$dogCommand: UnknownType){
                dog {
                    doesKnowCommand(dogCommand: \$dogCommand)
                }
            }"""
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.UnknownType
        validationErrors.get(0).message == "Validation error (UnknownType) : Unknown type 'UnknownType'"
    }

    def '5.7.3 Variables Are Input Types - non-null unknown type'() {
        def query = """
            query madDog(\$dogCommand: UnknownType!){
                dog {
                    doesKnowCommand(dogCommand: \$dogCommand)
                }
            }"""
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.UnknownType
        validationErrors.get(0).message == "Validation error (UnknownType) : Unknown type 'UnknownType'"
    }

    def '5.7.3 Variables Are Input Types - non-null list unknown type'() {
        def query = """
            query madDog(\$dogCommand: [UnknownType]){
                dog {
                    doesKnowCommand(dogCommand: \$dogCommand)
                }
            }"""
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.UnknownType
        validationErrors.get(0).message == "Validation error (UnknownType) : Unknown type 'UnknownType'"
    }

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}
