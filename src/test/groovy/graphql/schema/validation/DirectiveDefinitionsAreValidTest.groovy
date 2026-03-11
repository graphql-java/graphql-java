package graphql.schema.validation

import graphql.schema.GraphQLDirective
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema

class DirectiveDefinitionsAreValidTest extends Specification {

    def "detects directive definition name starting with __"() {
        when:
        def directive = GraphQLDirective.newDirective()
                .name("__badName")
                .validLocations(FIELD_DEFINITION)
                .build()

        def schema = newSchema()
                .query(newObject()
                        .name("Query")
                        .field(newFieldDefinition().name("field").type(GraphQLString).build())
                        .build())
                .additionalDirective(directive)
                .build()

        then:
        def e = thrown(InvalidSchemaException)
        e.getErrors().any {
            it.description.contains("__badName") && it.description.contains("must not begin with")
        }
    }

    def "detects directive argument name starting with __"() {
        when:
        def directive = GraphQLDirective.newDirective()
                .name("myDirective")
                .validLocations(FIELD_DEFINITION)
                .argument(newArgument().name("__badArg").type(GraphQLString).build())
                .build()

        def schema = newSchema()
                .query(newObject()
                        .name("Query")
                        .field(newFieldDefinition().name("field").type(GraphQLString).build())
                        .build())
                .additionalDirective(directive)
                .build()

        then:
        def e = thrown(InvalidSchemaException)
        e.getErrors().any {
            it.description.contains("__badArg") && it.description.contains("must not begin with")
        }
    }

    def "valid directive definitions pass"() {
        when:
        def directive = GraphQLDirective.newDirective()
                .name("myDirective")
                .validLocations(FIELD_DEFINITION)
                .argument(newArgument().name("arg1").type(GraphQLString).build())
                .build()

        def schema = newSchema()
                .query(newObject()
                        .name("Query")
                        .field(newFieldDefinition().name("field").type(GraphQLString).build())
                        .build())
                .additionalDirective(directive)
                .build()

        then:
        noExceptionThrown()
    }
}
