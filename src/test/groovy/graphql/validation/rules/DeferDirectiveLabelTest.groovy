package graphql.validation.rules

import graphql.ExperimentalApi
import graphql.GraphQLContext
import graphql.language.Document
import graphql.parser.Parser
import graphql.validation.LanguageTraversal
import graphql.validation.RulesVisitor
import graphql.validation.SpecValidationSchema
import graphql.validation.TraversalContext
import graphql.validation.ValidationContext
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class DeferDirectiveLabelTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)

    ValidationErrorCollector errorCollector = new ValidationErrorCollector()

    DeferDirectiveLabel deferDirectiveLabel = new DeferDirectiveLabel(validationContext, errorCollector)

    def setup() {
        def traversalContext = Mock(TraversalContext)
        validationContext.getSchema() >> SpecValidationSchema.specValidationSchema
        validationContext.getGraphQLContext() >> GraphQLContext.newContext().of(
                ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT, true
        ).build();
        validationContext.getTraversalContext() >> traversalContext
        validationContext.i18n(_, _) >> "test error message"
    }

    def "Allow unique label directive"() {
        given:
        def query = """
            query defer_query {
              ... @defer(label: "name") {
                human {
                  name
                }
              }
          }
        """
        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        when:
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [deferDirectiveLabel]))

        then:
        errorCollector.errors.isEmpty()

    }

    def "Defer directive label argument must be unique"() {
        given:
        def query = """
            query defer_query {
                dog {
                    ... @defer(label: "name") {
                        name 
                    }
                }
                alien {
                    ... @defer(label: "name") {
                        name 
                   }
                }
        
            }
        """

        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        when:
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [deferDirectiveLabel]))

        then:
        !errorCollector.errors.isEmpty()
        errorCollector.containsValidationError(ValidationErrorType.DuplicateIncrementalLabel)
    }

    def "Multiple use of Defer directive is valid"() {
        given:
        def query = """
            query defer_query {
                dog {
                    ... @defer {
                        name 
                    }
                    ... @defer {
                        name 
                    }
                }
            }
        """
        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        when:
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [deferDirectiveLabel]))

        then:
        errorCollector.errors.isEmpty()
    }

    def "Allow Multiple use of Defer directive with different labels"() {
        given:
        def query = """
            query defer_query {
                dog {
                    ... @defer(label: "name") {
                        name 
                    }
                    ... @defer(label: "nameAgain") {
                        name 
                    }
                }
            }
        """
        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        when:
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [deferDirectiveLabel]))

        then:
        errorCollector.errors.isEmpty()
    }


    def "Label cannot be an argument directive"() {
        given:
        def query = """
            query defer_query(\$label: Int) {
                ... @defer(label:\$label) {
                    human {
                      name
                    }
                }
            }
        """

        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        when:
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [deferDirectiveLabel]))

        then:
        !errorCollector.errors.isEmpty()
        errorCollector.containsValidationError(ValidationErrorType.WrongType)
    }


    def "Defer directive Label must be string"() {
        given:
        def query = """
          query defer_query {
            dog {
                ... @defer(label: 1) {
                    name 
                }
            }
         }
        """
        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        when:
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [deferDirectiveLabel]))

        then:
        !errorCollector.errors.isEmpty()
        errorCollector.errors.size() == 1
        errorCollector.containsValidationError(ValidationErrorType.WrongType)
    }

    def "defer with null label should behave as if no label was provided"() {
        def query = '''
            query {
                dog {
                    ... @defer(label: null) {
                        name
                    }
                }
                cat {
                    ... @defer(label: null) {
                        name
                    }
                }
            }
        '''
        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        when:
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [deferDirectiveLabel]))

        then:
        errorCollector.errors.isEmpty()
    }


        static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}

