package graphql.language

import spock.lang.Specification

class NodeWithNewChildrenTest extends Specification {

    def "fragment spread withNewChildren replaces directives"() {
        given:
        def replacementDirective = directive("include", true)
        def fragmentSpread = FragmentSpread.newFragmentSpread("Fields")
                .directive(directive("skip", false))
                .build()
        def newChildren = NodeChildrenContainer.newNodeChildrenContainer()
                .children(FragmentSpread.CHILD_DIRECTIVES, [replacementDirective])
                .build()

        when:
        def changed = fragmentSpread.withNewChildren(newChildren)

        then:
        changed.name == "Fields"
        changed.directives*.name == ["include"]
        changed.hasDirective("include")
        changed.getDirectives("include") == [replacementDirective]
        changed.directivesByName.keySet() == ["include"] as Set
    }

    def "variable definition withNewChildren replaces type default value and directives"() {
        given:
        def replacementDirective = directive("include", true)
        def variableDefinition = VariableDefinition.newVariableDefinition("value", TypeName.newTypeName("String").build(), StringValue.of("old"))
                .directive(directive("skip", false))
                .build()
        def newChildren = NodeChildrenContainer.newNodeChildrenContainer()
                .child(VariableDefinition.CHILD_TYPE, TypeName.newTypeName("ID").build())
                .child(VariableDefinition.CHILD_DEFAULT_VALUE, StringValue.of("new"))
                .children(VariableDefinition.CHILD_DIRECTIVES, [replacementDirective])
                .build()

        when:
        def changed = variableDefinition.withNewChildren(newChildren)

        then:
        changed.name == "value"
        changed.type.name == "ID"
        changed.defaultValue.value == "new"
        changed.directives*.name == ["include"]
        changed.hasDirective("include")
        changed.getDirectives("include") == [replacementDirective]
        changed.directivesByName.keySet() == ["include"] as Set
    }

    private static Directive directive(String name, boolean value) {
        Directive.newDirective()
                .name(name)
                .argument(Argument.newArgument("if", BooleanValue.of(value)).build())
                .build()
    }
}
