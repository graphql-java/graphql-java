package graphql.validation

import graphql.TestUtil
import graphql.i18n.I18n
import graphql.language.Document
import graphql.parser.Parser
import graphql.validation.LanguageTraversal
import graphql.validation.OperationValidationRule
import graphql.validation.OperationValidator
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class NoUndefinedVariablesTest extends Specification {
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()

    def traverse(String query) {
        Document document = new Parser().parseDocument(query)
        I18n i18n = I18n.i18n(I18n.BundleType.Validation, Locale.ENGLISH)
        ValidationContext validationContext = new ValidationContext(TestUtil.dummySchema, document, i18n)
        OperationValidator operationValidator = new OperationValidator(validationContext, errorCollector,
                { r -> r == OperationValidationRule.NO_UNDEFINED_VARIABLES })
        LanguageTraversal languageTraversal = new LanguageTraversal()

        languageTraversal.traverse(document, operationValidator)
    }

    def "undefined variable"() {
        given:
        def query = """
            query Foo(\$a: String, \$b: String, \$c: String) {
                field(a: \$a, b: \$b, c: \$c, d: \$d)
            }
        """

        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.UndefinedVariable)
        errorCollector.getErrors()[0].message == "Validation error (UndefinedVariable@[field]) : Undefined variable 'd'"
    }

    def "all variables defined"() {
        given:
        def query = """
            query Foo(\$a: String, \$b: String, \$c: String) {
                field(a: \$a, b: \$b, c: \$c)
            }
        """

        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()
    }

    def "all variables in fragments deeply defined"() {
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

    def "variable in fragment not defined by operation"() {
        given:
        def query = """
        query Foo(\$a: String, \$b: String) {
            ...FragA
        }
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
        """
        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.UndefinedVariable)
        errorCollector.getErrors()[0].message == "Validation error (UndefinedVariable@[FragA/field/FragB/field/FragC/field]) : Undefined variable 'c'"
    }

    def "floating fragment with variables"() {
        given:
        def query = """
        fragment A on Type {
            field(a: \$a)
        }
        """

        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()
    }

    def "multiple operations and completely defined variables"() {
        given:
        def query = """
        query Foo(\$a: String) { ...A }
        query Bar(\$a: String) { ...A }

        fragment A on Type {
            field(a: \$a)
        }
        """

        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()
    }

    def "multiple operations and mixed variable definitions"() {
        given:
        def query = """
        query Foo(\$a: String) { ...A }
        query Bar { ...A }

        fragment A on Type {
            field(a: \$a)
        }
        """

        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.UndefinedVariable)
        errorCollector.getErrors()[0].message == "Validation error (UndefinedVariable@[A/field]) : Undefined variable 'a'"
    }

    def "multiple operations with undefined variables"() {
        given:
        def query = """
        query Foo { ...A }
        query Bar { ...A }

        fragment A on Type {
            field(a: \$a)
        }
        """

        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.UndefinedVariable)
        errorCollector.getErrors()[0].message == "Validation error (UndefinedVariable@[A/field]) : Undefined variable 'a'"
    }
}
