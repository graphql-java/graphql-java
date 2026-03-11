package graphql.schema.validation

import graphql.TestUtil
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.introspection.Introspection.DirectiveLocation.ENUM
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema

class DirectiveApplicationIsValidTest extends Specification {

    def "detects directive used in wrong location via SDL"() {
        def sdl = '''
            directive @enumOnly on ENUM

            type Query @enumOnly {
                field : String
            }
        '''

        when:
        TestUtil.schema(sdl)

        then:
        def e = thrown(Exception)
        e.message.contains("enumOnly") || e.getErrors().any { it.toString().contains("enumOnly") }
    }

    def "detects directive used in wrong location via programmatic schema"() {
        when:
        def directive = TestUtil.mkDirective("enumOnly", ENUM)
        def field = newFieldDefinition()
                .name("hello")
                .type(GraphQLString)
                .withAppliedDirective(directive.toAppliedDirective())
                .build()

        def schema = newSchema()
                .query(newObject()
                        .name("Query")
                        .field(field)
                        .build())
                .additionalDirective(directive)
                .build()

        then:
        def e = thrown(InvalidSchemaException)
        e.getErrors().any {
            it.description.contains("enumOnly") && it.description.contains("FIELD_DEFINITION")
        }
    }

    def "no errors when directive is in correct location"() {
        def sdl = '''
            directive @myDir on FIELD_DEFINITION

            type Query {
                field : String @myDir
            }
        '''

        when:
        def schema = TestUtil.schema(sdl)

        then:
        noExceptionThrown()
    }
}
