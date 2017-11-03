package graphql.validation.rules

import graphql.StarWarsSchema
import graphql.language.Document
import graphql.parser.Parser
import graphql.validation.LanguageTraversal
import graphql.validation.RulesVisitor
import graphql.validation.TraversalContext
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import spock.lang.Specification

import static graphql.schema.visibility.GraphqlFieldVisibilityEnvironment.newEnvironment

class KnownDirectivesTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    KnownDirectives knownDirectives = new KnownDirectives(validationContext, errorCollector)

    def setup() {
        def traversalContext = Mock(TraversalContext)
        validationContext.getSchema() >> StarWarsSchema.starWarsSchema
        validationContext.getTraversalContext() >> traversalContext
    }


    def "misplaced directive"() {
        given:
        def query = """
          query Foo @include(if: true) {
                name
              }
        """

        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        when:
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [knownDirectives]), newEnvironment().build())

        then:
        errorCollector.containsValidationError(ValidationErrorType.MisplacedDirective)

    }

    def "well placed directive"() {
        given:
        def query = """
          query Foo  {
                name @include(if: true)
              }
        """

        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        when:
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [knownDirectives]), newEnvironment().build())

        then:
        errorCollector.errors.isEmpty()

    }


}
