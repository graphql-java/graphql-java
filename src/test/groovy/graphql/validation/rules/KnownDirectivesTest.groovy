package graphql.validation.rules

import graphql.StarWarsSchema
import graphql.language.Document
import graphql.parser.Parser
import graphql.validation.*
import spock.lang.Specification

class KnownDirectivesTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    KnownDirectives knownDirectives = new KnownDirectives(validationContext,errorCollector)

    def setup() {
        def traversalContext = Mock(TraversalContext)
        validationContext.getSchema() >> StarWarsSchema.starWarsSchema
        validationContext.getTraversalContext() >> traversalContext
    }


    def "misplaced directive"(){
        given:
        def query = """
          query Foo @include(if: true) {
                name
              }
        """

        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal();

        when:
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [knownDirectives]));

        then:
        errorCollector.containsValidationError(ValidationErrorType.MisplacedDirective)

    }

    def "well placed directive"(){
        given:
        def query = """
          query Foo  {
                name @include(if: true)
              }
        """

        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal();

        when:
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [knownDirectives]));

        then:
        errorCollector.errors.isEmpty()

    }




}
