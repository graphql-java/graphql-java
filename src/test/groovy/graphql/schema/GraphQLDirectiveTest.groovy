package graphql.schema

import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.introspection.Introspection.DirectiveLocation.ARGUMENT_DEFINITION
import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION
import static graphql.introspection.Introspection.DirectiveLocation.INTERFACE
import static graphql.introspection.Introspection.DirectiveLocation.OBJECT
import static graphql.introspection.Introspection.DirectiveLocation.UNION

class GraphQLDirectiveTest extends Specification {

    def "object can be transformed"() {
        given:
        def startingDirective = GraphQLDirective.newDirective()
                .name("D1")
                .description("D1_description")
                .validLocation(ARGUMENT_DEFINITION)
                .validLocations(FIELD_DEFINITION, OBJECT)
                .argument(GraphQLArgument.newArgument().name("argStr").type(GraphQLString))
                .argument(GraphQLArgument.newArgument().name("argInt").type(GraphQLInt))
                .build()
        when:
        def transformedDirective = startingDirective.transform({ builder ->
            builder.name("D2")
                    .description("D2_description")
                    .clearValidLocations()
                    .validLocations(INTERFACE, UNION)
                    .argument(GraphQLArgument.newArgument().name("argInt").type(GraphQLBoolean))
                    .argument(GraphQLArgument.newArgument().name("argIntAdded").type(GraphQLInt))
        })
        then:
        startingDirective.name == "D1"
        startingDirective.description == "D1_description"
        startingDirective.validLocations() == [ARGUMENT_DEFINITION, FIELD_DEFINITION, OBJECT].toSet()
        startingDirective.arguments.size() == 2
        startingDirective.getArgument("argStr").type == GraphQLString
        startingDirective.getArgument("argInt").type == GraphQLInt

        transformedDirective.name == "D2"
        transformedDirective.description == "D2_description"
        transformedDirective.validLocations() == [INTERFACE, UNION].toSet()
        transformedDirective.arguments.size() == 3
        transformedDirective.getArgument("argStr").type == GraphQLString
        transformedDirective.getArgument("argInt").type == GraphQLBoolean // swapped
        transformedDirective.getArgument("argIntAdded").type == GraphQLInt
    }
}
