package graphql.schema

import spock.lang.Specification

import static graphql.Scalars.GraphQLFloat
import static graphql.Scalars.GraphQLInt
import static graphql.schema.GraphQLDirective.newDirective
import static graphql.schema.GraphQLInputObjectField.newInputObjectField

class GraphQLInputObjectFieldTest extends Specification {

    def "object can be transformed"() {
        given:
        def startingField = newInputObjectField()
                .name("F1")
                .type(GraphQLFloat)
                .description("F1_description")
                .withDirective(newDirective().name("directive1"))
                .withDirective(newDirective().name("directive2"))
                .build()

        when:
        def transformedField = startingField.transform({ builder ->
            builder.name("F2")
                    .type(GraphQLInt)
                    .withDirective(newDirective().name("directive1"))
                    .withDirective(newDirective().name("directive3"))

        })


        then:

        startingField.name == "F1"
        startingField.type == GraphQLFloat
        startingField.description == "F1_description"

        startingField.getDirectives().size() == 2
        startingField.getDirective("directive1") != null
        startingField.getDirective("directive2") != null

        transformedField.name == "F2"
        transformedField.type == GraphQLInt
        transformedField.description == "F1_description" // left alone

        transformedField.getDirectives().size() == 3
        transformedField.getDirective("directive1") != null
        transformedField.getDirective("directive2") != null
        transformedField.getDirective("directive3") != null
    }
}
