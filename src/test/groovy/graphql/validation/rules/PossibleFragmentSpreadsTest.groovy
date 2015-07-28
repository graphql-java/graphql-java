package graphql.validation.rules

import graphql.language.Document
import graphql.parser.Parser
import graphql.validation.LanguageTraversal
import graphql.validation.RulesVisitor
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import spock.lang.Specification

class PossibleFragmentSpreadsTest extends Specification {


    ValidationErrorCollector errorCollector = new ValidationErrorCollector()

    def traverse(String query) {
        Document document = new Parser().parseDocument(query)
        ValidationContext validationContext = new ValidationContext(Harness.Schema, document)
        PossibleFragmentSpreads possibleFragmentSpreads = new PossibleFragmentSpreads(validationContext, errorCollector)
        LanguageTraversal languageTraversal = new LanguageTraversal();

        languageTraversal.traverse(document, new RulesVisitor(validationContext, [possibleFragmentSpreads]));
    }

    def 'of the same object'() {
        def query = """

                fragment objectWithinObject on Dog { ...dogFragment }
                fragment dogFragment on Dog { barkVolume }
        """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().isEmpty()
    }

    def 'of the same object with inline fragment'() {
        def query = """
                fragment objectWithinObjectAnon on Dog { ... on Dog { barkVolume } }
                """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().isEmpty()
    }


    def 'object into an implemented interface'() {
        def query = """
                fragment objectWithinInterface on Pet { ...dogFragment }
                fragment dogFragment on Dog { barkVolume }
                """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().isEmpty()
    }

    def 'object into containing union'() {
        def query = """
                fragment objectWithinUnion on CatOrDog { ...dogFragment }
                fragment dogFragment on Dog { barkVolume }
                """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().isEmpty()
    }

    def 'union into contained object'() {
        def query = """
                fragment unionWithinObject on Dog { ...catOrDogFragment }
                fragment catOrDogFragment on CatOrDog { __typename }
                 """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().isEmpty()
    }

    def 'union into overlapping interface'() {
        def query = """
                fragment unionWithinInterface on Pet { ...catOrDogFragment }
                fragment catOrDogFragment on CatOrDog { __typename }
                 """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().isEmpty()
    }

    def 'union into overlapping union'() {
        def query = """
                fragment unionWithinUnion on DogOrHuman { ...catOrDogFragment }
                fragment catOrDogFragment on CatOrDog { __typename }
                """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().isEmpty()
    }

    def 'interface into implemented object'() {
        def query = """
        fragment interfaceWithinObject on Dog { ... petFragment }
        fragment petFragment on Pet { name }
        """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().isEmpty()
    }

    def 'interface into overlapping interface'() {
        def query = """
        fragment interfaceWithinInterface on Pet { ... beingFragment }
        fragment beingFragment on Being { name }
        """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().isEmpty()
    }

    def 'interface into overlapping interface in inline fragment'() {
        def query = """
        fragment interfaceWithinInterface on Pet { ... on Being { name } }
        """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().isEmpty()
    }


    def 'interface into overlapping union'() {
        def query = """
        fragment interfaceWithinUnion on CatOrDog { ... petFragment }
        fragment petFragment on Pet { name }
        """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().isEmpty()
    }

}
