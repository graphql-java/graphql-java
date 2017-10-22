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

class NoFragmentCyclesTest extends Specification {

    ValidationErrorCollector errorCollector = new ValidationErrorCollector()

    def traverse(String query) {
        Document document = new Parser().parseDocument(query)
        ValidationContext validationContext = new ValidationContext(TestUtil.dummySchema, document)
        NoFragmentCycles noFragmentCycles = new NoFragmentCycles(validationContext, errorCollector)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        languageTraversal.traverse(document, new RulesVisitor(validationContext, [noFragmentCycles]))
    }

    def 'single reference is valid'() {
        given:
        def query = """
                fragment fragA on Dog { ...fragB }
                fragment fragB on Dog { name }
        """

        when:
        traverse(query)
        then:
        errorCollector.getErrors().isEmpty()
    }

    def 'spreading twice is not circular'() {
        given:
        def query = """
                fragment fragA on Dog { ...fragB, ...fragB }
                fragment fragB on Dog { name }
        """
        when:
        traverse(query)
        then:
        errorCollector.getErrors().isEmpty()

    }

    def 'spreading twice indirectly is not circular'() {
        given:
        def query = """
                fragment fragA on Dog { ...fragB, ...fragC }
                fragment fragB on Dog { ...fragC }
                fragment fragC on Dog { name }
        """
        when:
        traverse(query)
        then:
        errorCollector.getErrors().isEmpty()
    }

    def 'double spread within abstract types'() {
        given:
        def query = """
                fragment nameFragment on Pet {
            ... on Dog { name }
            ... on Cat { name }
        }

                fragment spreadsInAnon on Pet {
            ... on Dog { ...nameFragment }
            ... on Cat { ...nameFragment }
        }
        """
        when:
        traverse(query)
        then:
        errorCollector.getErrors().isEmpty()
    }


    def "circular fragments"() {
        given:
        def query = """
            fragment fragA on Dog { ...fragB }
            fragment fragB on Dog { ...fragA }
        """

        when:
        traverse(query)
        then:
        errorCollector.containsValidationError(ValidationErrorType.FragmentCycle)

    }


    def 'no spreading itself directly'() {
        given:
        def query = """
        fragment fragA on Dog { ...fragA }
        """
        when:
        traverse(query)
        then:
        errorCollector.containsValidationError(ValidationErrorType.FragmentCycle)

    }

    def "no spreading itself indirectly within inline fragment"() {
        given:
        def query = """
         fragment fragA on Pet {
            ... on Dog {
              ...fragB
            }
          }
          fragment fragB on Pet {
            ... on Dog {
              ...fragA
            }
          }
        """
        when:
        traverse(query)
        then:
        errorCollector.containsValidationError(ValidationErrorType.FragmentCycle)

    }

    def "no spreading itself deeply two paths"() {
        given:
        def query = """
            fragment fragA on Dog { ...fragB, ...fragC }
            fragment fragB on Dog { ...fragA }
            fragment fragC on Dog { ...fragA }
        """
        when:
        traverse(query)
        then:
        errorCollector.containsValidationError(ValidationErrorType.FragmentCycle)

    }

    def "#583 no npe on undefined fragment"() {
        given:
        def query = """
                fragment fragA on Dog { ...fragNotDefined }
                fragment fragB on Dog { name }
        """

        when:
        traverse(query)
        then:

        // no errors but KnownFragmentNames will pick this up
        errorCollector.getErrors().isEmpty()
    }

}
