package graphql.schema.validation

import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLSchema
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject

class DuplicateDirectiveDefinitionTest extends Specification {

    def "schema validation detects duplicate directive definitions"() {
        given:
        def directive1 = GraphQLDirective.newDirective()
                .name("myDirective")
                .validLocation(FIELD_DEFINITION)
                .build()
        def directive2 = GraphQLDirective.newDirective()
                .name("myDirective")
                .validLocation(FIELD_DEFINITION)
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition().name("field").type(GraphQLString))
                .build()

        when:
        GraphQLSchema.newSchema()
                .query(queryType)
                .additionalDirective(directive1)
                .additionalDirective(directive2)
                .build()

        then:
        def e = thrown(InvalidSchemaException)
        e.message.contains("Duplicate directive definition: 'myDirective'")
    }

    def "schema validation allows distinct directive definitions"() {
        given:
        def directive1 = GraphQLDirective.newDirective()
                .name("directiveA")
                .validLocation(FIELD_DEFINITION)
                .build()
        def directive2 = GraphQLDirective.newDirective()
                .name("directiveB")
                .validLocation(FIELD_DEFINITION)
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition().name("field").type(GraphQLString))
                .build()

        when:
        def schema = GraphQLSchema.newSchema()
                .query(queryType)
                .additionalDirective(directive1)
                .additionalDirective(directive2)
                .build()

        then:
        noExceptionThrown()
        schema.getDirective("directiveA") != null
        schema.getDirective("directiveB") != null
    }

    def "schema validator reports correct error type for duplicate directives"() {
        given:
        def directive1 = GraphQLDirective.newDirective()
                .name("dupDirective")
                .validLocation(FIELD_DEFINITION)
                .build()
        def directive2 = GraphQLDirective.newDirective()
                .name("dupDirective")
                .validLocation(FIELD_DEFINITION)
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition().name("field").type(GraphQLString))
                .build()

        when:
        GraphQLSchema.newSchema()
                .query(queryType)
                .additionalDirective(directive1)
                .additionalDirective(directive2)
                .build()

        then:
        def e = thrown(InvalidSchemaException)
        e.errors.any {
            it.classification == SchemaValidationErrorType.DuplicateDirectiveDefinition
        }
    }
}
