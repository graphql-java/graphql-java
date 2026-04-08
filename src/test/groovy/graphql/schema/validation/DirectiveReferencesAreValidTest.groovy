package graphql.schema.validation

import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLTypeReference
import spock.lang.Specification

import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.introspection.Introspection.DirectiveLocation.ARGUMENT_DEFINITION
import static graphql.introspection.Introspection.DirectiveLocation.INPUT_FIELD_DEFINITION
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema

class DirectiveReferencesAreValidTest extends Specification {

    def "programmatic schemas reject directive cycles through directives"() {
        given:
        def first = GraphQLDirective.newDirective()
                .name("first")
                .validLocation(ARGUMENT_DEFINITION)
                .build()
        def second = GraphQLDirective.newDirective()
                .name("second")
                .validLocation(ARGUMENT_DEFINITION)
                .argument(newArgument()
                        .name("arg")
                        .type(GraphQLString)
                        .withAppliedDirective(first.toAppliedDirective())
                        .build())
                .build()
        first = first.transform { builder ->
            builder.argument(newArgument()
                    .name("arg")
                    .type(GraphQLString)
                    .withAppliedDirective(second.toAppliedDirective())
                    .build())
        }

        when:
        newSchema()
                .query(newObject()
                        .name("Query")
                        .field(newFieldDefinition()
                                .name("hello")
                                .type(GraphQLString)
                                .build())
                        .build())
                .additionalDirective(first)
                .additionalDirective(second)
                .build()

        then:
        def error = thrown(InvalidSchemaException)
        error.message.contains("first")
        error.message.contains("second")
    }

    def "programmatic schemas reject directive cycles through input types"() {
        given:
        def first = GraphQLDirective.newDirective()
                .name("first")
                .validLocation(ARGUMENT_DEFINITION)
                .argument(newArgument()
                        .name("arg")
                        .type(GraphQLTypeReference.typeRef("Filter"))
                        .build())
                .build()
        def second = GraphQLDirective.newDirective()
                .name("second")
                .validLocations(ARGUMENT_DEFINITION, INPUT_FIELD_DEFINITION)
                .argument(newArgument()
                        .name("arg")
                        .type(GraphQLInt)
                        .withAppliedDirective(first.toAppliedDirective())
                        .build())
                .build()
        def filterType = newInputObject()
                .name("Filter")
                .field(newInputObjectField()
                        .name("field")
                        .type(GraphQLInt)
                        .withAppliedDirective(second.toAppliedDirective())
                        .build())
                .build()

        when:
        newSchema()
                .query(newObject()
                        .name("Query")
                        .field(newFieldDefinition()
                                .name("hello")
                                .type(GraphQLString)
                                .argument(newArgument()
                                        .name("filter")
                                        .type(filterType)
                                        .build())
                                .build())
                        .build())
                .additionalType(filterType)
                .additionalDirective(first)
                .additionalDirective(second)
                .build()

        then:
        def error = thrown(InvalidSchemaException)
        error.message.contains("first")
        error.message.contains("Filter")
        error.message.contains("second")
    }
}
