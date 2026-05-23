package graphql.schema.validation

import graphql.AssertException
import graphql.TestUtil
import graphql.schema.GraphQLDirective
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema

class NoDirectiveRedefinitionTest extends Specification {

    def "directive cannot be redefined in SDL schema"() {
        given:
        def sdl = '''
            directive @exampleDirective on FIELD_DEFINITION
            directive @exampleDirective on FIELD_DEFINITION

            type Query {
                hello: String @exampleDirective
            }
        '''

        when:
        TestUtil.schema(sdl)

        then:
        def schemaProblem = thrown(AssertionError)
        schemaProblem.message.contains("tried to redefine existing directive 'exampleDirective'")
    }

    def "programmatically redefined directive is rejected"() {
        when:
        newSchema()
                .query(newObject()
                        .name("Query")
                        .field(newFieldDefinition()
                                .name("hello")
                                .type(GraphQLString))
                        .build())
                .additionalDirective(exampleDirective())
                .additionalDirective(exampleDirective())
                .build()

        then:
        def exception = thrown(AssertException)
        exception.message == "Directive 'exampleDirective' already exists with a different instance"
    }

    private static GraphQLDirective exampleDirective() {
        GraphQLDirective.newDirective()
                .name("exampleDirective")
                .validLocation(FIELD_DEFINITION)
                .build()
    }
}
