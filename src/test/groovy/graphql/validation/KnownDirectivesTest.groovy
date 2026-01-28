package graphql.validation

import graphql.StarWarsSchema
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
import graphql.validation.Validator
import spock.lang.Specification

class KnownDirectivesTest extends Specification {

    ValidationErrorCollector errorCollector = new ValidationErrorCollector()

    def traverse(String query) {
        Document document = new Parser().parseDocument(query)
        I18n i18n = I18n.i18n(I18n.BundleType.Validation, Locale.ENGLISH)
        ValidationContext validationContext = new ValidationContext(StarWarsSchema.starWarsSchema, document, i18n)
        OperationValidator operationValidator = new OperationValidator(validationContext, errorCollector,
                { r -> r == OperationValidationRule.KNOWN_DIRECTIVES })
        LanguageTraversal languageTraversal = new LanguageTraversal()
        languageTraversal.traverse(document, operationValidator)
    }


    def "misplaced directive"() {
        given:
        def query = """
          query Foo @include(if: true) {
                name
              }
        """

        when:
        traverse(query)

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

        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()

    }

    def "correct placed directive on mutation"() {
        given:
        def query = """
          mutation Foo  {
                name @include(if: true)
              }
        """

        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()

    }


    def "misplaced directive on mutation"() {
        given:
        def query = """
          mutation Foo @include(if: true) {
                name
              }
        """

        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.MisplacedDirective)

    }



    def "bogus directive"() {
        given:
        def query = """
          query Foo @helloWorld {
                name
              }
        """

        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.UnknownDirective)

    }


    def "misplaced directive on fragment definition"() {
        given:
        def query = """
          fragment getName on Foo @include(if: true) {
            name
          }
          query Foo {
                ...getName
              }
        """

        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.MisplacedDirective)

    }


    def "directive on external fragment spread"() {
        given:
        def query = """
          fragment getName on Foo {
             ... @include(if: true) {
                    name
             }
          }
          query Foo {
                ...getName
              }
        """

        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()

    }

    def "directive on inline fragment spread"() {
        given:
        def query = """
          query Foo {
                 ... @include(if: true) {
                    name
                  }
              }
        """

        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()

    }

    def "directive on inline fragment spread and type"() {
        given:
        def query = """
          query Foo {
                 ... on Foo @include(if: true) {
                    name
                  }
              }
        """

        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()

    }

    def "directive on fragment spread"() {
        given:
        def query = """
          fragment getName on Foo {
            name
          }
          query Foo {
                ...getName @include(if: true)
              }
        """

        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()

    }

    def sdl = '''

        directive @queryDirective on QUERY

        directive @subDirective on SUBSCRIPTION

        type Query {
            field: String
        }

        type Subscription {
            field: String
        }

    '''

    def schema = TestUtil.schema(sdl)

    def "invalid directive on SUBSCRIPTION"() {
        def spec = '''
            subscription sub @queryDirective{
                field
            }
        '''

        when:
        def document = TestUtil.parseQuery(spec)
        def validator = new Validator()
        def validationErrors = validator.validateDocument(schema, document, Locale.ENGLISH)

        then:
        validationErrors.size() == 1
        validationErrors.get(0).validationErrorType == ValidationErrorType.MisplacedDirective
        validationErrors.get(0).message == "Validation error (MisplacedDirective) : Directive 'queryDirective' not allowed here"
    }

    def "unknown directive on SUBSCRIPTION"() {
        def spec = '''
            subscription sub @unknownDirective{
                field
            }
        '''

        when:
        def document = TestUtil.parseQuery(spec)
        def validator = new Validator()
        def validationErrors = validator.validateDocument(schema, document, Locale.ENGLISH)

        then:
        validationErrors.size() == 1
        validationErrors.get(0).validationErrorType == ValidationErrorType.UnknownDirective
        validationErrors.get(0).message == "Validation error (UnknownDirective) : Unknown directive 'unknownDirective'"
    }

    def "valid directive on SUBSCRIPTION"() {
        def spec = '''
            subscription sub @subDirective{
                field
            }
        '''

        when:
        def document = TestUtil.parseQuery(spec)
        def validator = new Validator()
        def validationErrors = validator.validateDocument(schema, document, Locale.ENGLISH)

        then:
        validationErrors.size() == 0
    }

}
