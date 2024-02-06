package graphql.validation.rules

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

class DeferDirectivesTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()

    DeferDirectiveLabel deferDirectiveLabel = new DeferDirectiveLabel(validationContext, errorCollector)

    def setup() {
        def traversalContext = Mock(TraversalContext)
        validationContext.getSchema() >> SpecValidationSchema.specValidationSchema
        validationContext.getTraversalContext() >> traversalContext
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
        errorCollector.containsValidationError(ValidationErrorType.DuplicateArgumentNames)
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
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.isEmpty()
    }

    def "Multiple use of Defer directive with different labels is valid"() {
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
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.isEmpty()
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
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.isEmpty()
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.WrongType
        validationErrors.get(0).message == "Validation error (WrongType@[dog]) : argument 'label' with value 'IntValue{value=1}' is not a valid 'String' - Expected an AST type of 'StringValue' but it was a 'IntValue'"
    }

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}

