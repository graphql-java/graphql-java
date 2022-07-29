package graphql.execution


import graphql.language.Argument
import graphql.language.BooleanValue
import graphql.language.Directive
import spock.lang.Specification

class ConditionalNodesTest extends Specification {

    def "should include false for skip = true"() {
        given:
        def variables = new LinkedHashMap<String, Object>()
        ConditionalNodes conditionalNodes = new ConditionalNodes()

        def argument = Argument.newArgument("if", new BooleanValue(true)).build()
        def directives = [Directive.newDirective().name("skip").arguments([argument]).build()]

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
