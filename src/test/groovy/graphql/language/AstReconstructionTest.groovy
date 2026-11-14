package graphql.language

import graphql.parser.Parser
import spock.lang.Specification

class AstReconstructionTest extends Specification {
    def "generic child replacement preserves executable document values types and locations"() {
        given:
        def document = Parser.parse('''query Q($values: [[Int!]!]!, $flag: Boolean = false) {
            f(int: 1, float: 1.5, string: "text", enum: RED, null: null, bool: true, variable: $flag,
              list: [1, null], object: {value: 2})
            ... @include(if: $flag) { a }
            ... on T { b }
        }''')

        when:
        def rebuilt = rebuild(document)

        then:
        AstPrinter.printAst(rebuilt) == AstPrinter.printAst(document)
        rebuilt.sourceLocation == document.sourceLocation
        def originalType = document.getDefinitionsOfType(OperationDefinition)[0].variableDefinitions[0].type
        def rebuiltType = rebuilt.getDefinitionsOfType(OperationDefinition)[0].variableDefinitions[0].type
        !rebuiltType.is(originalType)
        graphql.schema.idl.TypeUtil.simplePrint(rebuiltType) == '[[Int!]!]!'
        graphql.schema.idl.TypeUtil.isWrapped(rebuiltType)
    }

    def "sorting keeps untyped inline scopes distinct from typed scopes"() {
        given:
        def document = Parser.parse('{ ... on Z { z } ... @include(if: true) { a } ... on A { b } }')

        when:
        def sorted = new AstSorter().sort(document)

        then:
        sorted.getDefinitionsOfType(OperationDefinition)[0].selectionSet.selections*.typeCondition*.name == [null, 'A', 'Z']
    }

    private static Node rebuild(Node node) {
        def children = node.namedChildren.children.collectEntries { name, nodes -> [(name): nodes.collect { rebuild(it) }] }
        node.withNewChildren(NodeChildrenContainer.newNodeChildrenContainer(children).build())
    }
}
