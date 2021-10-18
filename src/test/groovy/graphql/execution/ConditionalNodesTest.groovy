package graphql.execution

import graphql.AssertException
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

    def "throws if no variable present for condition"() {
        given:
        def variables = new LinkedHashMap<String, Object>()
        ConditionalNodes conditionalNodes = new ConditionalNodes()
        conditionalNodes.valuesResolver = Mock(ValuesResolver)

        def argument = Argument.newArgument("if", new BooleanValue(true)).build()
        def directives = [Directive.newDirective().name("skip").arguments([argument]).build()]

        conditionalNodes.valuesResolver.getArgumentValues(Directives.SkipDirective.getArguments(), [argument], variables) >> [:]

        when:
        conditionalNodes.shouldInclude(variables, directives)

        then:
        thrown AssertException
    }

    def "returns default value for condition if allowMissingVariables is true and variable missing"() {
        given:
        def variables = new LinkedHashMap<String, Object>()
        ConditionalNodes conditionalNodes = new ConditionalNodes()
        conditionalNodes.allowMissingVariables(true)
        conditionalNodes.valuesResolver = Mock(ValuesResolver)

        def argument = Argument.newArgument("if", new BooleanValue(true)).build()
        def directives = [Directive.newDirective().name(directiveName).arguments([argument]).build()]

        conditionalNodes.valuesResolver.getArgumentValues(Directives.SkipDirective.getArguments(), [argument], variables) >> condition

        when:
        def shouldInclude = conditionalNodes.shouldInclude(variables, directives)

        then:
        shouldInclude == expected

        where:
        directiveName | condition     | expected
        "include"     | ["if": false] | false
        "include"     | ["if": true]  | true
        "include"     | [:]           | true
        "skip"        | ["if": false] | true
        "skip"        | ["if": true]  | false
        "skip"        | [:]           | true
    }
}
