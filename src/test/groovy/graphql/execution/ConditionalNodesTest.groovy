package graphql.execution

import graphql.Directives
import graphql.language.Argument
import graphql.language.BooleanValue
import graphql.language.Directive
import spock.lang.Specification

class ConditionalNodesTest extends Specification {


    def "should include false for skip = true"() {
        given:
        def variables = new LinkedHashMap<String, Object>()
        ConditionalNodes conditionalNodes = new ConditionalNodes()
        conditionalNodes.valuesResolver = Mock(ValuesResolver)

        def argument = Argument.newArgument("if", new BooleanValue(true)).build()
        def directives = [Directive.newDirective().name("skip").arguments([argument]).build()]

        conditionalNodes.valuesResolver.getArgumentValues(Directives.SkipDirective.getArguments(), [argument], variables) >> ["if": true]

        expect:
        !conditionalNodes.shouldInclude(variables, directives)
    }

    def "no directives means include"() {
        given:
        def variables = new LinkedHashMap<String, Object>()
        ConditionalNodes conditionalNodes = new ConditionalNodes()

        expect:
        conditionalNodes.shouldInclude(variables, [])

    }
}
