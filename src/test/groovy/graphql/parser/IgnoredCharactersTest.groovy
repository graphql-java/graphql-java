package graphql.parser

import graphql.language.IgnoredChars
import graphql.language.Node
import spock.lang.Specification

class IgnoredCharactersTest extends Specification {

    def "#nodeType captures surrounding ignored characters only when enabled"() {
        given:
        def left = ' \t\r\n,'
        def right = ',\r\t\n '
        def source = template.replace('<', left).replace('>', right)

        when:
        def captured = parseNode(source, nodeType, true)

        then:
        captured != null
        captured.ignoredChars.left*.value.join() == left
        captured.ignoredChars.right*.value.join() == right

        when:
        def uncaptured = parseNode(source, nodeType, false)

        then:
        uncaptured != null
        uncaptured.ignoredChars.is(IgnoredChars.EMPTY)

        where:
        nodeType                             | template
        'Document'                           | '<{ f }>'
        'OperationDefinition'                | '<query Q { f }>'
        'SelectionSet'                       | 'query Q<{ f }>'
        'Field'                              | '{<f>}'
        'Argument'                           | '{ f(<arg: 1>) }'
        'VariableDefinition'                 | 'query Q(<$v: Int>) { f }'
        'VariableReference'                  | '{ f(arg:<$v>) }'
        'FragmentDefinition'                 | '<fragment F on Query { f }>'
        'FragmentSpread'                     | '{<...F>}'
        'InlineFragment'                     | '{<... on Query { f }>}'
        'Directive'                          | '{ f<@d>}'
        'IntValue'                           | '{ f(arg:<1>) }'
        'FloatValue'                         | '{ f(arg:<1.5>) }'
        'BooleanValue'                       | '{ f(arg:<true>) }'
        'NullValue'                          | '{ f(arg:<null>) }'
        'StringValue'                        | '{ f(arg:<"value">) }'
        'EnumValue'                          | '{ f(arg:<VALUE>) }'
        'ArrayValue'                         | '{ f(arg:<[1]>) }'
        'ObjectValue'                        | '{ f(arg:<{key: 1}>) }'
        'TypeName'                           | 'query Q($v:<Int>) { f }'
        'ListType'                           | 'query Q($v:<[Int]>) { f }'
        'NonNullType'                        | 'query Q($v:<Int!>) { f }'
        'SchemaDefinition'                   | '<schema { query: Query }>'
        'SchemaExtensionDefinition'          | '<extend schema @d>'
        'OperationTypeDefinition'            | 'schema {<query: Query>}'
        'ScalarTypeDefinition'               | '<scalar S>'
        'ScalarTypeExtensionDefinition'      | '<extend scalar S @d>'
        'ObjectTypeDefinition'               | '<type Query { f: Int }>'
        'ObjectTypeExtensionDefinition'      | '<extend type Query { f: Int }>'
        'FieldDefinition'                    | 'type Query {<f: Int>}'
        'InterfaceTypeDefinition'            | '<interface I { f: Int }>'
        'InterfaceTypeExtensionDefinition'   | '<extend interface I { f: Int }>'
        'UnionTypeDefinition'                | '<union U = Query>'
        'UnionTypeExtensionDefinition'       | '<extend union U = Query>'
        'EnumTypeDefinition'                 | '<enum E { VALUE }>'
        'EnumTypeExtensionDefinition'        | '<extend enum E { VALUE }>'
        'EnumValueDefinition'                | 'enum E {<VALUE>}'
        'InputObjectTypeDefinition'          | '<input I { f: Int }>'
        'InputObjectTypeExtensionDefinition' | '<extend input I { f: Int }>'
        'InputValueDefinition'               | 'input I {<f: Int>}'
        'DirectiveDefinition'                | '<directive @d on FIELD>'
        'DirectiveExtensionDefinition'       | '<extend directive @d @other>'
        'DirectiveLocation'                  | 'directive @d on<FIELD>'
    }

    private static Node parseNode(String source, String nodeType, boolean captureIgnoredChars) {
        def options = ParserOptions.newParserOptions().captureIgnoredChars(captureIgnoredChars).build()
        def environment = ParserEnvironment.newParserEnvironment().document(source).parserOptions(options).build()
        return findNode(Parser.parse(environment), nodeType)
    }

    private static Node findNode(Node node, String nodeType) {
        if (node.class.simpleName == nodeType) {
            return node
        }
        return node.children.findResult { findNode(it, nodeType) }
    }
}
