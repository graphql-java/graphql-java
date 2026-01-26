package graphql.validation.rules

import graphql.Scalars
import graphql.language.Field
import graphql.language.SelectionSet
import graphql.parser.Parser
import graphql.schema.GraphQLObjectType
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationContext
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

import static graphql.language.Field.newField

class ScalarLeavesTest extends Specification {

    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    ValidationContext validationContext = Mock(ValidationContext)
    ScalarLeaves scalarLeaves = new ScalarLeaves(validationContext, errorCollector)

    def setup() {
        validationContext.i18n(_, _) >> "test error message"
    }

    def "subselection not allowed"() {
        given:
        Field field = newField("hello", SelectionSet.newSelectionSet([newField("world").build()]).build()).build()
        validationContext.getOutputType() >> Scalars.GraphQLString
        when:
        scalarLeaves.checkField(field)

        then:
        errorCollector.containsValidationError(ValidationErrorType.SubselectionNotAllowed)
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
        given:
        Field field = newField("hello").build()
        validationContext.getOutputType() >> GraphQLObjectType.newObject().name("objectType").build()
        when:
        scalarLeaves.checkField(field)

        then:
        errorCollector.containsValidationError(ValidationErrorType.SubselectionRequired)
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
