package graphql.validation.rules

import graphql.Scalars
import graphql.language.Document
import graphql.parser.Parser
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.validation.*
import spock.lang.Ignore
import spock.lang.Specification

class OverlappingFieldsCanBeMergedTest extends Specification {

    ValidationErrorCollector errorCollector = new ValidationErrorCollector()


    def traverse(String query) {
        def objectType = GraphQLObjectType.newObject()
                .name("Test")
                .field(GraphQLFieldDefinition.newFieldDefinition().name("name").type(Scalars.GraphQLString).build())
                .field(GraphQLFieldDefinition.newFieldDefinition().name("nickname").type(Scalars.GraphQLString).build())
                .build();
        def schema = GraphQLSchema.newSchema().query(objectType).build()

        Document document = new Parser().parseDocument(query)
        ValidationContext validationContext = new ValidationContext(schema, document)
        OverlappingFieldsCanBeMerged overlappingFieldsCanBeMerged = new OverlappingFieldsCanBeMerged(validationContext, errorCollector)
        LanguageTraversal languageTraversal = new LanguageTraversal();

        languageTraversal.traverse(document, new RulesVisitor(validationContext, [overlappingFieldsCanBeMerged]));
    }

    @Ignore
    def "identical fields are ok"() {
        given:
        def query = """
            fragment f on Test{
                name
                name
            }
        """
        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()
    }

    @Ignore
    def "two aliases with different targets"() {
        given:
        def query = """
            fragment f on Test{
                myName : name
                myName : nickname
            }
        """
        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.FieldsConflict)
    }

}
