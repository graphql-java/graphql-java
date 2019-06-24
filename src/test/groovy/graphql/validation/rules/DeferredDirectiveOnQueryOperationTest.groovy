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

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLNonNull.nonNull

class DeferredDirectiveOnQueryOperationTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    DeferredDirectiveOnQueryOperation directiveOnQueryOperation = new DeferredDirectiveOnQueryOperation(validationContext, errorCollector)

    void setup() {
        def traversalContext = Mock(TraversalContext)
        validationContext.getSchema() >> StarWarsSchema.starWarsSchema
        validationContext.getTraversalContext() >> traversalContext

        def fieldDefinition = newFieldDefinition().name("field").type(nonNull(GraphQLString)).build()

        validationContext.getParentType() >> StarWarsSchema.humanType
        validationContext.getFieldDef() >> fieldDefinition
    }

    def "denies operations that are not queries"() {
        given:
        def query = """
          mutation Foo {
                name @defer
              }
        """

        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        when:
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [directiveOnQueryOperation]))

        then:
        errorCollector.containsValidationError(ValidationErrorType.DeferDirectiveNotOnQueryOperation)
    }

    def "allows operations that are queries"() {
        given:
        def query = """
          query Foo {
                name @defer
              }
        """

        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        when:
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [directiveOnQueryOperation]))

        then:
        errorCollector.errors.isEmpty()
    }

}
