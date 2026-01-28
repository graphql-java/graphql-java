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

class NoFragmentCyclesTest extends Specification {

    ValidationErrorCollector errorCollector = new ValidationErrorCollector()

    def traverse(String query) {
        Document document = new Parser().parseDocument(query)
        I18n i18n = I18n.i18n(I18n.BundleType.Validation, Locale.ENGLISH)
        ValidationContext validationContext = new ValidationContext(TestUtil.dummySchema, document, i18n)
        OperationValidator operationValidator = new OperationValidator(validationContext, errorCollector,
                { r -> r == OperationValidationRule.NO_FRAGMENT_CYCLES })
        LanguageTraversal languageTraversal = new LanguageTraversal()
        languageTraversal.traverse(document, operationValidator)
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
        errorCollector.getErrors()[0].message == "Validation error (FragmentCycle@[fragA]) : Fragment cycles not allowed"
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
        errorCollector.getErrors()[0].message == "Validation error (FragmentCycle@[fragA]) : Fragment cycles not allowed"
    }

    def "no self-spreading in floating fragments"() {
        given:
        def query = """
        fragment fragA on Dog {
          ...fragA
        }
        """

        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.FragmentCycle)
        errorCollector.getErrors()[0].message == "Validation error (FragmentCycle@[fragA]) : Fragment cycles not allowed"
    }

    def "no co-recursive spreads in floating fragments"() {
        given:
        def query = """
        fragment fragB on Dog { ...fragA }
        fragment fragA on Dog { ...fragB }
        """

        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.FragmentCycle)
        errorCollector.getErrors()[0].message == "Validation error (FragmentCycle@[fragB]) : Fragment cycles not allowed"
    }

    def "no co-recursive spreads in non-initial fragments"() {
        given:
        def query = """
          fragment fragA on Dog { ...fragB }
          fragment fragB on Dog { ...fragC }
          fragment fragC on Doc { ...fragB }
        """

        when:
        traverse(query)
        then:
        errorCollector.containsValidationError((ValidationErrorType.FragmentCycle))
    }

    def "mix of inline fragments and fragments"() {
        given:
        def query = """
            fragment Foo on Foo {
                ... on Type1 { ...Bar }
                ... on Type2 { ...Baz }
            }

            fragment Bar on Bar { ...Baz }
            fragment Baz on Baz { x }
        """

        when:
        traverse(query)
        then:
        errorCollector.getErrors().isEmpty()
    }

    def "no self-spread fragments used in multiple operations"() {
        given:
        def query = """
            fragment fragA on Dog { ...fragA }
            query A { ...fragA }
            query B { ...fragA }
        """

        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.FragmentCycle)
        errorCollector.getErrors()[0].message == "Validation error (FragmentCycle@[fragA]) : Fragment cycles not allowed"
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

    def "#1817 no stack overflow on circular fragment"() {
        given:
        def query = """
                query {
                    ...MyFrag
                }
                fragment MyFrag on QueryType {
                    field
                    ...MyFrag
                }
        """

        def document = Parser.parse(query)

        I18n i18n = I18n.i18n(I18n.BundleType.Validation, Locale.ENGLISH)
        def validationContext = new ValidationContext(TestUtil.dummySchema, document, i18n)
        def operationValidator = new OperationValidator(validationContext, errorCollector,
                { r -> r == OperationValidationRule.NO_FRAGMENT_CYCLES })
        when:
        LanguageTraversal languageTraversal = new LanguageTraversal()
        languageTraversal.traverse(document, operationValidator)

        then:

        !errorCollector.getErrors().isEmpty()
        errorCollector.containsValidationError(ValidationErrorType.FragmentCycle)
        errorCollector.getErrors()[0].message == "Validation error (FragmentCycle@[MyFrag]) : Fragment cycles not allowed"
    }
}
