package graphql.validation.rules

import graphql.TestUtil
import graphql.language.Document
import graphql.parser.Parser
import graphql.validation.LanguageTraversal
import graphql.validation.RulesVisitor
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class NoUndefinedVariablesTest extends Specification {


    ValidationErrorCollector errorCollector = new ValidationErrorCollector()


    def traverse(String query) {
        Document document = new Parser().parseDocument(query)
        ValidationContext validationContext = new ValidationContext(TestUtil.dummySchema, document)
        NoUndefinedVariables noUndefinedVariables = new NoUndefinedVariables(validationContext, errorCollector)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        languageTraversal.traverse(document, new RulesVisitor(validationContext, [noUndefinedVariables]))
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

    }

    def "all variables definied"() {
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

    def 'variable in fragment not defined by operation'() {
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

    }
}
