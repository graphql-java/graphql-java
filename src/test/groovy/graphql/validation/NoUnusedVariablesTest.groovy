package graphql.validation

import graphql.TestUtil
import graphql.i18n.I18n
import graphql.language.Document
import graphql.parser.Parser
import graphql.validation.LanguageTraversal
import graphql.validation.OperationValidationRule
import graphql.validation.OperationValidator
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class NoUnusedVariablesTest extends Specification {
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()

    def traverse(String query) {
        Document document = new Parser().parseDocument(query)
        I18n i18n = I18n.i18n(I18n.BundleType.Validation, Locale.ENGLISH)
        ValidationContext validationContext = new ValidationContext(TestUtil.dummySchema, document, i18n)
        OperationValidator operationValidator = new OperationValidator(validationContext, errorCollector,
                { r -> r == OperationValidationRule.NO_UNUSED_VARIABLES })
        LanguageTraversal languageTraversal = new LanguageTraversal()

        languageTraversal.traverse(document, operationValidator)
    }

    def "uses all variables in fragments"() {
        given:
        def query = """
        fragment FragA on Type {
            field(a: \$a) {
                ...FragB
            }
        }
        fragment FragB on Type {
            field(b: \$b) {
                ...FragC
            }
        }
        fragment FragC on Type {
            field(c: \$c)
        }
        query Foo(\$a: String, \$b: String, \$c: String) {
            ...FragA
        }
        """
        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()
    }

    def "variable used by fragment in multiple operations"() {
        given:
        def query = """
          query Foo(\$a: String) {
            ...FragA
          }
          query Bar(\$b: String) {
            ...FragB
          }
          fragment FragA on Type {
            field(a: \$a)
          }
          fragment FragB on Type {
            field(b: \$b)
          }
          """

        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()
    }

    def "variables not used"() {
        given:
        def query = """
        query Foo(\$a: String, \$b: String, \$c: String) {
            field(a: \$a, b: \$b)
        }
        """
        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.UnusedVariable)
    }

    def "variables not used in fragments"() {
        given:
        def query = """
        fragment FragA on Type {
            field(a: \$a) {
                ...FragB
            }
        }
        fragment FragB on Type {
            field(b: \$b) {
                ...FragC
            }
        }
        fragment FragC on Type {
            __typename
        }
        query Foo(\$a: String, \$b: String, \$c: String) {
            ...FragA
        }
        """

        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.UnusedVariable)
    }

    def "variables not used in fragments with error message"() {
        def query = '''
                query getDogName($arg1: String, $unusedArg: Int) {
                  dog(arg1: $arg1) {
                      name
                  }
                }
            '''
        when:
        def document = Parser.parse(query)
        def validationErrors = new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.UnusedVariable
        validationErrors.get(0).message == "Validation error (UnusedVariable) : Unused variable 'unusedArg'"
    }
}
