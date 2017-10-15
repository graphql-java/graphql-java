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

class NoUnusedVariablesTest extends Specification {


    ValidationErrorCollector errorCollector = new ValidationErrorCollector()


    def traverse(String query) {
        Document document = new Parser().parseDocument(query)
        ValidationContext validationContext = new ValidationContext(TestUtil.dummySchema, document)
        NoUnusedVariables noUnusedVariables = new NoUnusedVariables(validationContext, errorCollector)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        languageTraversal.traverse(document, new RulesVisitor(validationContext, [noUnusedVariables]))
    }


    def 'uses all variables in fragments'() {
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


}
