package graphql.schema

import graphql.language.FloatValue
import spock.lang.Specification

import static graphql.Scalars.GraphQLFloat
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLDirective.newDirective

class GraphQLArgumentTest extends Specification {

    def "object can be transformed"() {
        given:
        def startingArgument = GraphQLArgument.newArgument().name("A1")
                .description("A1_description")
                .type(GraphQLInt)
                .deprecate("custom reason")
                .withDirective(newDirective().name("directive1"))
                .build()
        when:
        def transformedArgument = startingArgument.transform({
            it
                    .name("A2")
                    .description("A2_description")
                    .type(GraphQLString)
                    .withDirective(newDirective().name("directive3"))
                    .value("VALUE")
                    .deprecate(null)
                    .defaultValue("DEFAULT")
        })

        then:
        startingArgument.name == "A1"
        startingArgument.description == "A1_description"
        startingArgument.type == GraphQLInt
        startingArgument.argumentDefaultValue.value == null
        startingArgument.deprecationReason == "custom reason"
        startingArgument.isDeprecated()
        startingArgument.getDirectives().size() == 1
        startingArgument.getDirective("directive1") != null

        transformedArgument.name == "A2"
        transformedArgument.description == "A2_description"
        transformedArgument.type == GraphQLString
        transformedArgument.argumentValue.value == "VALUE"
        transformedArgument.argumentDefaultValue.value == "DEFAULT"
        transformedArgument.deprecationReason == null
        !transformedArgument.isDeprecated()
        transformedArgument.getDirectives().size() == 2
        transformedArgument.getDirective("directive1") != null
        transformedArgument.getDirective("directive3") != null
    }

    def "object can be transformed without setting the default value"() {
        given:
        def startingArgument = GraphQLArgument.newArgument().name("A1")
                .type(GraphQLInt)
                .build()
        when:
        def transformedArgument = startingArgument.transform({
            it.name("A2")
        })

        then:

        transformedArgument.name == "A2"
        !transformedArgument.hasSetDefaultValue()
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
                .replaceDirectives([
                        newDirective().name("directive1").build(),
                        newDirective().name("directive2").build(),
                        newDirective().name("directive3").build()]) // overwrite
                .build()

        then:
        argument.getDirectives().size() == 3
        argument.getDirective("directive1") != null
        argument.getDirective("directive2") != null
        argument.getDirective("directive3") != null
    }

    def "can get values statically"() {
        when:
        GraphQLArgument startingArg = GraphQLArgument.newArgument()
                .name("F1")
                .type(GraphQLFloat)
                .description("F1_description")
                .valueProgrammatic(4.56d)
                .defaultValueProgrammatic(1.23d)
                .build()
        def inputValue = startingArg.getArgumentValue()
        def resolvedValue = GraphQLArgument.getArgumentValue(startingArg)

        def inputDefaultValue = startingArg.getArgumentDefaultValue()
        def resolvedDefaultValue = GraphQLArgument.getArgumentDefaultValue(startingArg)

        then:
        inputValue.isExternal()
        inputValue.getValue() == 4.56d
        resolvedValue == 4.56d

        inputDefaultValue.isExternal()
        inputDefaultValue.getValue() == 1.23d
        resolvedDefaultValue == 1.23d

        when:
        startingArg = GraphQLArgument.newArgument()
                .name("F1")
                .type(GraphQLFloat)
                .description("F1_description")
                .valueLiteral(FloatValue.newFloatValue().value(4.56d).build())
                .defaultValueLiteral(FloatValue.newFloatValue().value(1.23d).build())
                .build()

        inputValue = startingArg.getArgumentValue()
        resolvedValue = GraphQLArgument.getArgumentValue(startingArg)

        inputDefaultValue = startingArg.getArgumentDefaultValue()
        resolvedDefaultValue = GraphQLArgument.getArgumentDefaultValue(startingArg)

        then:

        inputValue.isLiteral()
        (inputValue.getValue() as FloatValue).getValue().toDouble() == 4.56d
        resolvedValue == 4.56d

        inputDefaultValue.isLiteral()
        (inputDefaultValue.getValue() as FloatValue).getValue().toDouble() == 1.23d
        resolvedDefaultValue == 1.23d

        when:
        startingArg = GraphQLArgument.newArgument()
                .name("F1")
                .type(GraphQLFloat)
                .description("F1_description")
                .build()

        inputValue = startingArg.getArgumentValue()
        resolvedValue = GraphQLArgument.getArgumentValue(startingArg)

        inputDefaultValue = startingArg.getArgumentDefaultValue()
        resolvedDefaultValue = GraphQLArgument.getArgumentDefaultValue(startingArg)

        then:

        inputValue.isNotSet()
        inputValue.getValue() == null
        resolvedValue == null

        inputDefaultValue.isNotSet()
        inputDefaultValue.getValue() == null
        resolvedDefaultValue == null
    }

}
