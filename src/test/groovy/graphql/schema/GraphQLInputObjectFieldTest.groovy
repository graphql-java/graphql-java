package graphql.schema

import graphql.language.FloatValue
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
                .deprecate("No longer useful")
                .build()

        when:
        def transformedField = startingField.transform({ builder ->
            builder.name("F2")
                    .type(GraphQLInt)
                    .deprecate(null)
                    .withDirective(newDirective().name("directive3"))

        })


        then:

        startingField.name == "F1"
        startingField.type == GraphQLFloat
        startingField.description == "F1_description"
        startingField.isDeprecated()
        startingField.getDeprecationReason() == "No longer useful"

        startingField.getDirectives().size() == 2
        startingField.getDirective("directive1") != null
        startingField.getDirective("directive2") != null

        transformedField.name == "F2"
        transformedField.type == GraphQLInt
        transformedField.description == "F1_description" // left alone

        !transformedField.isDeprecated()
        transformedField.getDeprecationReason() == null

        transformedField.getDirectives().size() == 3
        transformedField.getDirective("directive1") != null
        transformedField.getDirective("directive2") != null
        transformedField.getDirective("directive3") != null
    }

    def "can get default values statically"() {
        when:
        def startingField = newInputObjectField()
                .name("F1")
                .type(GraphQLFloat)
                .description("F1_description")
                .defaultValueProgrammatic(1.23d)
                .build()
        def inputValue = startingField.getInputFieldDefaultValue()
        def resolvedValue = GraphQLInputObjectField.getInputFieldDefaultValue(startingField)

        then:
        inputValue.isExternal()
        inputValue.getValue() == 1.23d
        resolvedValue == 1.23d

        when:
        startingField = newInputObjectField()
                .name("F1")
                .type(GraphQLFloat)
                .description("F1_description")
                .defaultValueLiteral(FloatValue.newFloatValue().value(1.23d).build())
                .build()
        inputValue = startingField.getInputFieldDefaultValue()
        resolvedValue = GraphQLInputObjectField.getInputFieldDefaultValue(startingField)

        then:
        inputValue.isLiteral()
        (inputValue.getValue() as FloatValue).getValue().toDouble() == 1.23d
        resolvedValue == 1.23d

        when: " we have no values "
        startingField = newInputObjectField()
                .name("F1")
                .type(GraphQLFloat)
                .description("F1_description")
                .build()
        inputValue = startingField.getInputFieldDefaultValue()
        resolvedValue = GraphQLInputObjectField.getInputFieldDefaultValue(startingField)

        then:
        inputValue.isNotSet()
        inputValue.getValue() == null
        resolvedValue == null
    }
}
