package graphql.language

import graphql.util.TraversalControl
import graphql.util.TraverserContext
import spock.lang.Specification
import spock.lang.Unroll

import static java.util.Collections.emptyList

class NodeVisitorStubTest extends Specification {


    @Unroll
    def "#visitMethod call visitSelection by default"() {
        given:
        NodeVisitorStub nodeVisitorStub = Spy(NodeVisitorStub, constructorArgs: [])
        TraverserContext context = Mock(TraverserContext)

        when:
        def control = nodeVisitorStub."$visitMethod"(node, context)
        then:
        1 * nodeVisitorStub.visitSelection(node, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        where:
        node                 | visitMethod
        new Field()          | 'visitField'
        new FragmentSpread() | 'visitFragmentSpread'
        new InlineFragment() | 'visitInlineFragment'

    }

    @Unroll
    def "#visitMethod call visitValue by default"() {
        given:
        NodeVisitorStub nodeVisitorStub = Spy(NodeVisitorStub, constructorArgs: [])
        TraverserContext context = Mock(TraverserContext)

        when:
        def control = nodeVisitorStub."$visitMethod"(node, context)
        then:
        1 * nodeVisitorStub.visitValue(node, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        where:
        node                    | visitMethod
        new FloatValue()        | 'visitFloatValue'
        new ArrayValue()        | 'visitArrayValue'
        new IntValue()          | 'visitIntValue'
        new BooleanValue(true)  | 'visitBooleanValue'
        NullValue.Null          | 'visitNullValue'
        new ObjectValue()       | 'visitObjectValue'
        new VariableReference() | 'visitVariableReference'
        new EnumValue()         | 'visitEnumValue'
        new StringValue()       | 'visitStringValue'
    }

    @Unroll
    def "#visitMethod call visitDefinition by default"() {
        given:
        NodeVisitorStub nodeVisitorStub = Spy(NodeVisitorStub, constructorArgs: [])
        TraverserContext context = Mock(TraverserContext)

        when:
        def control = nodeVisitorStub."$visitMethod"(node, context)
        then:
        1 * nodeVisitorStub.visitDefinition(node, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT


        where:
        node                        | visitMethod
        new OperationDefinition()   | 'visitOperationDefinition'
        new FragmentDefinition()    | 'visitFragmentDefinition'
        new DirectiveDefinition("") | 'visitDirectiveDefinition'
        new SchemaDefinition()      | 'visitSchemaDefinition'
    }

    @Unroll
    def "#visitMethod call visitTypeDefinition by default"() {
        given:
        NodeVisitorStub nodeVisitorStub = Spy(NodeVisitorStub, constructorArgs: [])
        TraverserContext context = Mock(TraverserContext)

        when:
        def control = nodeVisitorStub."$visitMethod"(node, context)
        then:
        1 * nodeVisitorStub.visitTypeDefinition(node, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        where:
        node                              | visitMethod
        new UnionTypeDefinition("")       | 'visitUnionTypeDefinition'
        new InputObjectTypeDefinition("") | 'visitInputObjectTypeDefinition'
        new ScalarTypeDefinition("")      | 'visitScalarTypeDefinition'
        new InterfaceTypeDefinition("")   | 'visitInterfaceTypeDefinition'
        new EnumTypeDefinition("")        | 'visitEnumTypeDefinition'
        new ObjectTypeDefinition("")      | 'visitObjectTypeDefinition'
    }

    @Unroll
    def "#visitMethod call visitTypes by default"() {
        given:
        NodeVisitorStub nodeVisitorStub = Spy(NodeVisitorStub, constructorArgs: [])
        TraverserContext context = Mock(TraverserContext)

        when:
        def control = nodeVisitorStub."$visitMethod"(node, context)
        then:
        1 * nodeVisitorStub.visitType(node, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        where:
        node              | visitMethod
        new NonNullType() | 'visitNonNullType'
        new ListType()    | 'visitListType'
        new TypeName("")  | 'visitTypeName'

    }

    @Unroll
    def "default for #visitMethod is to call visitNode"() {
        given:
        NodeVisitorStub nodeVisitorStub = Spy(NodeVisitorStub, constructorArgs: [])
        TraverserContext context = Mock(TraverserContext)

        when:
        def control = nodeVisitorStub."$visitMethod"(node, context)
        then:
        1 * nodeVisitorStub.visitNode(node, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        where:
        node                           | visitMethod
        new Argument("", null)         | 'visitArgument'
        new Directive("", emptyList()) | 'visitDirective'
        new DirectiveLocation("")      | 'visitDirectiveLocation'
        new Document()                 | 'visitDocument'
        new EnumValueDefinition("")    | 'visitEnumValueDefinition'
        new FieldDefinition("")        | 'visitFieldDefinition'
        new InputValueDefinition("")   | 'visitInputValueDefinition'
        new InputValueDefinition("")   | 'visitInputValueDefinition'
        new ObjectField("", null)      | 'visitObjectField'
        new OperationTypeDefinition()  | 'visitOperationTypeDefinition'
        new OperationTypeDefinition()  | 'visitOperationTypeDefinition'
        new SelectionSet()             | 'visitSelectionSet'
        new VariableDefinition()       | 'visitVariableDefinition'
        new StringValue("")            | 'visitValue'
        new OperationDefinition()      | 'visitDefinition'
        new UnionTypeDefinition("")    | 'visitTypeDefinition'
        new Field()                    | 'visitSelection'
        new NonNullType()              | 'visitType'

    }

}
