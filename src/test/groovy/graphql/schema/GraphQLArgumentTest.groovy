package graphql.schema

import spock.lang.Specification

import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLDirective.newDirective

class GraphQLArgumentTest extends Specification {

    def "object can be transformed"() {
        given:
        def startingArgument = GraphQLArgument.newArgument().name("A1")
                .description("A1_description")
                .type(GraphQLInt)
                .withDirective(newDirective().name("directive1"))
                .build()
        when:
        def transformedArgument = startingArgument.transform({
            it
                    .name("A2")
                    .description("A2_description")
                    .type(GraphQLString)
                    .withDirective(newDirective().name("directive1"))
                    .withDirective(newDirective().name("directive3"))
                    .value("VALUE")
                    .defaultValue("DEFAULT")
        })

        then:
        startingArgument.name == "A1"
        startingArgument.description == "A1_description"
        startingArgument.type == GraphQLInt
        startingArgument.defaultValue == null
        startingArgument.getDirectives().size() == 1
        startingArgument.getDirective("directive1") != null

        transformedArgument.name == "A2"
        transformedArgument.description == "A2_description"
        transformedArgument.type == GraphQLString
        transformedArgument.value == "VALUE"
        transformedArgument.defaultValue == "DEFAULT"
        transformedArgument.getDirectives().size() == 2
        transformedArgument.getDirective("directive1") != null
        transformedArgument.getDirective("directive3") != null
    }

    def "directive support on arguments via builder"() {

        def argument

        given:
        def builder = GraphQLArgument.newArgument().name("A1")
                .type(GraphQLInt)
                .withDirective(newDirective().name("directive1"))

        when:
        argument = builder.build()

        then:
        argument.getDirectives().size() == 1
        argument.getDirective("directive1") != null
        argument.getDirective("directive2") == null
        argument.getDirective("directive3") == null


        when:
        argument = builder
                .clearDirectives()
                .withDirective(newDirective().name("directive2"))
                .withDirective(newDirective().name("directive3"))
                .build()

        then:
        argument.getDirectives().size() == 2
        argument.getDirective("directive1") == null
        argument.getDirective("directive2") != null
        argument.getDirective("directive3") != null

        when:
        argument = builder
                .withDirective(newDirective().name("directive1"))
                .withDirective(newDirective().name("directive2")) // overwrite
                .withDirective(newDirective().name("directive3")) // overwrite
                .build()

        then:
        argument.getDirectives().size() == 3
        argument.getDirective("directive1") != null
        argument.getDirective("directive2") != null
        argument.getDirective("directive3") != null
    }
}
