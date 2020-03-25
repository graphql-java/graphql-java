package graphql

import graphql.language.Document
import graphql.parser.Parser
import graphql.validation.LanguageTraversal
import graphql.validation.RulesVisitor
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import graphql.validation.rules.DeferredMustBeOnAllFields
import graphql.validation.rules.NoFragmentCycles
import spock.lang.Specification

class Issue1817 extends Specification {

    ValidationErrorCollector errorCollector = new ValidationErrorCollector()

    def traverse(String query) {
        Document document = new Parser().parseDocument(query)
        ValidationContext validationContext = new ValidationContext(TestUtil.dummySchema, document)
        NoFragmentCycles noFragmentCycles = new NoFragmentCycles(validationContext, errorCollector)
        DeferredMustBeOnAllFields deferredMustBeOnAllFields = new DeferredMustBeOnAllFields(validationContext, errorCollector)
        LanguageTraversal languageTraversal = new LanguageTraversal()
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [noFragmentCycles, deferredMustBeOnAllFields]))
    }

    def '#1817 - Do not check if deferred is on all fields when a fragment cycle has been encountered'() {
        given:
        def query = """
                fragment MyFrag on SomeType {
                   text @defer
                   ...MyFrag
                }
                query {
                   MyFrag {
                      text
                  }
                }
        """

        when:
        traverse(query)
        then:
        errorCollector.getErrors().size() == 1
        errorCollector.containsValidationError(ValidationErrorType.FragmentCycle)
    }
}
