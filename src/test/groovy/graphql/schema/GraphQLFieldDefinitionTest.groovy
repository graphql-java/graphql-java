package graphql.schema

import graphql.AssertException
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLFloat
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLDirective.newDirective
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition

class GraphQLFieldDefinitionTest extends Specification {

    def "dataFetcher can't be null"() {
        when:
        newFieldDefinition().dataFetcher(null)
        then:
        def exception = thrown(AssertException)
        exception.getMessage().contains("dataFetcher")
    }

    def "object can be transformed"() {
        given:
        def startingField = newFieldDefinition()
                .name("F1")
                .type(GraphQLFloat)
                .description("F1_description")
                .deprecate("F1_deprecated")
                .argument(newArgument().name("argStr").type(GraphQLString))
                .argument(newArgument().name("argInt").type(GraphQLInt))
                .withDirective(newDirective().name("directive1"))
                .withDirective(newDirective().name("directive2"))
                .build()

        when:
        def transformedField = startingField.transform({ builder ->
            builder.name("F2")
                    .type(GraphQLInt)
                    .deprecate(null)
                    .argument(newArgument().name("argStr").type(GraphQLString))
                    .argument(newArgument().name("argInt").type(GraphQLBoolean))
                    .argument(newArgument().name("argIntAdded").type(GraphQLInt))
                    .withDirective(newDirective().name("directive1"))
                    .withDirective(newDirective().name("directive3"))

        })


        then:

        startingField.name == "F1"
        startingField.type == GraphQLFloat
        startingField.description == "F1_description"
        startingField.deprecated
        startingField.deprecationReason == "F1_deprecated"
        startingField.getArguments().size() == 2
        startingField.getArgument("argStr").type == GraphQLString
        startingField.getArgument("argInt").type == GraphQLInt

        startingField.getDirectives().size() == 2
        startingField.getDirective("directive1") != null
        startingField.getDirective("directive2") != null

        transformedField.name == "F2"
        transformedField.type == GraphQLInt
        transformedField.description == "F1_description" // left alone
        !transformedField.deprecated
        transformedField.deprecationReason == null
        transformedField.getArguments().size() == 3
        transformedField.getArgument("argStr").type == GraphQLString
        transformedField.getArgument("argInt").type == GraphQLBoolean
        transformedField.getArgument("argIntAdded").type == GraphQLInt

        transformedField.getDirectives().size() == 3
        transformedField.getDirective("directive1") != null
        transformedField.getDirective("directive2") != null
        transformedField.getDirective("directive3") != null
    }
}
