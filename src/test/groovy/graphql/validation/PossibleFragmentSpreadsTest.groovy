package graphql.validation

import graphql.i18n.I18n
import graphql.language.Document
import graphql.parser.Parser
import graphql.validation.LanguageTraversal
import graphql.validation.OperationValidationRule
import graphql.validation.OperationValidator
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import spock.lang.Specification

class PossibleFragmentSpreadsTest extends Specification {


    ValidationErrorCollector errorCollector = new ValidationErrorCollector()

    def traverse(String query) {
        Document document = new Parser().parseDocument(query)
        I18n i18n = I18n.i18n(I18n.BundleType.Validation, Locale.ENGLISH)
        ValidationContext validationContext = new ValidationContext(Harness.Schema, document, i18n)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        languageTraversal.traverse(document, new OperationValidator(validationContext, errorCollector,
                { rule -> rule == OperationValidationRule.POSSIBLE_FRAGMENT_SPREADS }))
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


    def 'different object into object'() {
        def query = """
                fragment invalidObjectWithinObject on Cat { ...dogFragment }
                fragment dogFragment on Dog { barkVolume }
                """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().size() == 1
    }

    def 'different object into object in inline fragment'() {
        def query = """
        fragment invalidObjectWithinObjectAnon on Cat {
            ... on Dog { barkVolume }
        }
                """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors().get(0).message == "Validation error (InvalidFragmentType@[invalidObjectWithinObjectAnon]) : Fragment cannot be spread here as objects of type 'Cat' can never be of type 'Dog'"
    }

    def 'object into not implementing interface'() {
        def query = """
                fragment invalidObjectWithinInterface on Pet { ...humanFragment }
                fragment humanFragment on Human { pets { name } }
                """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors().get(0).message == "Validation error (InvalidFragmentType@[invalidObjectWithinInterface]) : Fragment 'humanFragment' cannot be spread here as objects of type 'Pet' can never be of type 'Human'"
    }

    def 'object into not containing union'() {
        def query = """
                fragment invalidObjectWithinUnion on CatOrDog { ...humanFragment }
                fragment humanFragment on Human { pets { name } }
                """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().size() == 1
    }

    def 'union into not contained object'() {
        def query = """
                fragment invalidUnionWithinObject on Human { ...catOrDogFragment }
                fragment catOrDogFragment on CatOrDog { __typename }
                """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().size() == 1
    }

    def 'union into non overlapping interface'() {
        def query = """
                fragment invalidUnionWithinInterface on Pet { ...humanOrAlienFragment }
                fragment humanOrAlienFragment on HumanOrAlien { __typename }
                """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().size() == 1
    }

    def 'union into non overlapping union'() {
        def query = """
                fragment invalidUnionWithinUnion on CatOrDog { ...humanOrAlienFragment }
                fragment humanOrAlienFragment on HumanOrAlien { __typename }
                """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().size() == 1
    }

    def 'interface into non implementing object'() {
        def query = """
                fragment invalidInterfaceWithinObject on Cat { ...intelligentFragment }
                fragment intelligentFragment on Intelligent { iq }
                """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().size() == 1

    }

    def 'interface into non overlapping interface'() {
        def query = """
                fragment invalidInterfaceWithinInterface on Pet {
            ...intelligentFragment
        }
                fragment intelligentFragment on Intelligent { iq }
                """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().size() == 1
    }

    def 'interface into non overlapping interface in inline fragment'() {
        def query = """
                fragment invalidInterfaceWithinInterfaceAnon on Pet {
            ...on Intelligent { iq }
        }
                """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().size() == 1

    }

    def 'interface into non overlapping union'() {
        def query = """
                fragment invalidInterfaceWithinUnion on HumanOrAlien { ...petFragment }
                fragment petFragment on Pet { name }
                """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().size() == 1

    }

    def 'when fragment target type is not composite type do not error - FragmentsOnCompositeType takes care of the validation'() {
        setup: "LeashInput is an input type so it shouldn't be target-able"
        def query = """
           query {
            dogWithInput {
             ...LeashInputFragment
            }
           }

           fragment LeashInputFragment on LeashInput {
            id
           }
        """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().isEmpty()
    }

    def 'when inline fragment target type is not composite type do not error - FragmentsOnCompositeType takes care of the validation'() {
        setup: "LeashInput is an input type so it shouldn't be target-able"
        def query = """
           query {
            dogWithInput {
             ...on LeashInput {
              id
             }
            }
           }
        """
        when:
        traverse(query)

        then:
        errorCollector.getErrors().isEmpty()
    }

}
