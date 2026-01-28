package graphql.validation

import graphql.ExperimentalApi
import graphql.i18n.I18n
import graphql.language.Document
import graphql.parser.Parser
import graphql.validation.LanguageTraversal
import graphql.validation.OperationValidationRule
import graphql.validation.OperationValidator
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationContext
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class DeferDirectiveLabelTest extends Specification {

    ValidationErrorCollector errorCollector = new ValidationErrorCollector()

    def traverse(String query) {
        Document document = new Parser().parseDocument(query)
        I18n i18n = I18n.i18n(I18n.BundleType.Validation, Locale.ENGLISH)
        ValidationContext validationContext = new ValidationContext(SpecValidationSchema.specValidationSchema, document, i18n)
        validationContext.getGraphQLContext().put(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT, true)
        LanguageTraversal languageTraversal = new LanguageTraversal()
        languageTraversal.traverse(document, new OperationValidator(validationContext, errorCollector,
                { rule -> rule == OperationValidationRule.DEFER_DIRECTIVE_LABEL }))
    }

    def "Allow unique label directive"() {
        given:
        def query = """
            query defer_query {
              ... @defer(label: "name") {
                human {
                  name
                }
              }
          }
        """

        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()

    }

    def "Defer directive label argument must be unique"() {
        given:
        def query = """
            query defer_query {
                dog {
                    ... @defer(label: "name") {
                        name
                    }
                }
                alien {
                    ... @defer(label: "name") {
                        name
                   }
                }

            }
        """

        when:
        traverse(query)

        then:
        !errorCollector.errors.isEmpty()
        errorCollector.containsValidationError(ValidationErrorType.DuplicateIncrementalLabel)
    }

    def "Multiple use of Defer directive is valid"() {
        given:
        def query = """
            query defer_query {
                dog {
                    ... @defer {
                        name
                    }
                    ... @defer {
                        name
                    }
                }
            }
        """

        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()
    }

    def "Allow Multiple use of Defer directive with different labels"() {
        given:
        def query = """
            query defer_query {
                dog {
                    ... @defer(label: "name") {
                        name
                    }
                    ... @defer(label: "nameAgain") {
                        name
                    }
                }
            }
        """

        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()
    }


    def "Label cannot be an argument directive"() {
        given:
        def query = """
            query defer_query(\$label: Int) {
                ... @defer(label:\$label) {
                    human {
                      name
                    }
                }
            }
        """

        when:
        traverse(query)

        then:
        !errorCollector.errors.isEmpty()
        errorCollector.containsValidationError(ValidationErrorType.WrongType)
    }


    def "Defer directive Label must be string"() {
        given:
        def query = """
          query defer_query {
            dog {
                ... @defer(label: 1) {
                    name
                }
            }
         }
        """

        when:
        traverse(query)

        then:
        !errorCollector.errors.isEmpty()
        errorCollector.errors.size() == 1
        errorCollector.containsValidationError(ValidationErrorType.WrongType)
    }

    def "defer with null label should behave as if no label was provided"() {
        def query = '''
            query {
                dog {
                    ... @defer(label: null) {
                        name
                    }
                }
                cat {
                    ... @defer(label: null) {
                        name
                    }
                }
            }
        '''

        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()
    }


        static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}
