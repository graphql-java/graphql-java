package graphql.schema

import static graphql.introspection.Introspection.DirectiveLocation
import spock.lang.Specification

import static graphql.schema.GraphQLEnumValueDefinition.newEnumValueDefinition
import static graphql.TestUtil.mkDirective

class GraphQLEnumValueDefinitionTest extends Specification {
    def "object can be transformed"() {
        given:
        def startEnumValue = newEnumValueDefinition().name("EV1")
                .description("EV1_description")
                .value("A")
                .withDirective(mkDirective("directive1", DirectiveLocation.ENUM_VALUE))
                .build()
        when:
        def transformedEnumValue = startEnumValue.transform({
            it
                    .name("EV2")
                    .value("X")
                    .withDirective(mkDirective("directive2", DirectiveLocation.ENUM_VALUE))
        })

        then:
        startEnumValue.name == "EV1"
        startEnumValue.description == "EV1_description"
        startEnumValue.value == "A"
        startEnumValue.getDirectives().size() == 1
        startEnumValue.getDirective("directive1") != null


        transformedEnumValue.name == "EV2"
        transformedEnumValue.description == "EV1_description" // left alone
        transformedEnumValue.value == "X"
        transformedEnumValue.getDirectives().size() == 2
        transformedEnumValue.getDirective("directive1") != null
        transformedEnumValue.getDirective("directive2") != null
    }

}
