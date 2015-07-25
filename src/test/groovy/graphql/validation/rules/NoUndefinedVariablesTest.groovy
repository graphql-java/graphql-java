package graphql.validation.rules

import graphql.language.Document
import graphql.parser.Parser
import graphql.validation.*
import spock.lang.Specification

class NoUndefinedVariablesTest extends Specification {


    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    NoUndefinedVariables noUndefinedVariables = new NoUndefinedVariables(validationContext, errorCollector)

    def setup() {
        def traversalContext = Mock(TraversalContext)
        validationContext.getTraversalContext() >> traversalContext
    }


    def "undefined variable"() {
        given:
        def query = """
            query Foo(\$a: String, \$b: String, \$c: String) {
                field(a: \$a, b: \$b, c: \$c, d: \$d)
            }
        """

        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal();

        when:
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [noUndefinedVariables]));

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

        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal();

        when:
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [noUndefinedVariables]));

        then:
        errorCollector.errors.isEmpty()

    }
}
