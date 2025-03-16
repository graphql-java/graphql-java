package graphql.schema

import graphql.collect.ImmutableKit
import static graphql.introspection.Introspection.DirectiveLocation.ARGUMENT_DEFINITION
import graphql.language.FloatValue
import graphql.schema.validation.InvalidSchemaException
import spock.lang.Specification

import static graphql.Scalars.GraphQLFloat
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema
import static graphql.TestUtil.mkDirective

class GraphQLArgumentTest extends Specification {

    def "object can be transformed"() {
        given:
        def startingArgument = GraphQLArgument.newArgument().name("A1")
                .description("A1_description")
                .type(GraphQLInt)
                .deprecate("custom reason")
                .withDirective(mkDirective("directive1", ARGUMENT_DEFINITION))
                .build()
        when:
        def transformedArgument = startingArgument.transform({
            it
                    .name("A2")
                    .description("A2_description")
                    .type(GraphQLString)
                    .withDirective(mkDirective("directive3", ARGUMENT_DEFINITION))
                    .value("VALUE") // Retain deprecated for test coverage
                    .deprecate(null)
                    .defaultValue("DEFAULT") // Retain deprecated for test coverage
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
        transformedArgument.argumentValue.value == "VALUE" // Retain deprecated for test coverage
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
        def builder = newArgument().name("A1")
                .type(GraphQLInt)
                .withDirective(mkDirective("directive1", ARGUMENT_DEFINITION))

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
                .withDirective(mkDirective("directive2", ARGUMENT_DEFINITION))
                .withDirective(mkDirective("directive3", ARGUMENT_DEFINITION))
                .build()

        then:
        argument.getDirectives().size() == 2
        argument.getDirective("directive1") == null
        argument.getDirective("directive2") != null
        argument.getDirective("directive3") != null

        when:
        argument = builder
                .replaceDirectives([
                        mkDirective("directive1", ARGUMENT_DEFINITION),
                        mkDirective("directive2", ARGUMENT_DEFINITION),
                        mkDirective("directive3", ARGUMENT_DEFINITION)]) // overwrite
                .build()

        then:
        argument.getDirectives().size() == 3
        argument.getDirective("directive1") != null
        argument.getDirective("directive2") != null
        argument.getDirective("directive3") != null
    }

    def "can get values statically"() {
        // Retain deprecated API usages in this test for test coverage
        when:
        GraphQLArgument startingArg = GraphQLArgument.newArgument()
                .name("F1")
                .type(GraphQLFloat)
                .description("F1_description")
                .valueProgrammatic(4.56d) // Retain deprecated for test coverage
                .defaultValueProgrammatic(1.23d)
                .build()
        def inputValue = startingArg.getArgumentValue() // Retain deprecated for test coverage
        def resolvedValue = GraphQLArgument.getArgumentValue(startingArg) // Retain deprecated for test coverage

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
                .valueLiteral(FloatValue.newFloatValue().value(4.56d).build()) // Retain deprecated for test coverage
                .defaultValueLiteral(FloatValue.newFloatValue().value(1.23d).build())
                .build()

        inputValue = startingArg.getArgumentValue() // Retain deprecated for test coverage
        resolvedValue = GraphQLArgument.getArgumentValue(startingArg) // Retain deprecated for test coverage

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

        inputValue = startingArg.getArgumentValue() // Retain deprecated for test coverage
        resolvedValue = GraphQLArgument.getArgumentValue(startingArg) // Retain deprecated for test coverage

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

    def "schema directive arguments are validated for programmatic schemas"() {
        given:
        def arg = newArgument().name("arg").type(GraphQLInt).valueProgrammatic(ImmutableKit.emptyMap()).build() // Retain for test coverage
        def directive = mkDirective("cached", ARGUMENT_DEFINITION, arg)
        def field = newFieldDefinition()
            .name("hello")
            .type(GraphQLString)
            .argument(arg)
            .withDirective(directive)
            .build()
        when:
        newSchema()
            .query(
                newObject()
                    .name("Query")
                    .field(field)
                    .build()
            )
            .additionalDirective(directive)
            .build()
        then:
        def e = thrown(InvalidSchemaException)
        e.message.contains("Invalid argument 'arg' for applied directive of name 'cached'")
    }

    def "applied directive arguments are validated for programmatic schemas"() {
        given:
        def arg = newArgument()
                .name("arg")
                .type(GraphQLNonNull.nonNull(GraphQLInt))
                .build()
        def directive = mkDirective("cached", ARGUMENT_DEFINITION, arg)
        def field = newFieldDefinition()
            .name("hello")
            .type(GraphQLString)
            .withAppliedDirective(directive.toAppliedDirective())
            .build()
        when:
        newSchema()
            .query(
                newObject()
                    .name("Query")
                    .field(field)
                    .build()
            )
            .additionalDirective(directive)
            .build()
        then:
        def e = thrown(InvalidSchemaException)
        e.message.contains("Invalid argument 'arg' for applied directive of name 'cached'")
    }

}
