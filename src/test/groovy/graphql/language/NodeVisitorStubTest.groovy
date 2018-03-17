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

    def "type definitions call visitTypeDefinition by default"() {
        given:
        NodeVisitorStub nodeVisitorStub = Spy(NodeVisitorStub, constructorArgs: [])
        UnionTypeDefinition unionTypeDefinition = new UnionTypeDefinition("")
        InputObjectTypeDefinition inputObjectTypeDefinition = new InputObjectTypeDefinition("")
        ScalarTypeDefinition scalarTypeDefinition = new ScalarTypeDefinition("")
        InterfaceTypeDefinition interfaceTypeDefinition = new InterfaceTypeDefinition("")
        EnumTypeDefinition enumTypeDefinition = new EnumTypeDefinition("")
        ObjectTypeDefinition objectTypeDefinition = new ObjectTypeDefinition("")
        TraverserContext context = Mock(TraverserContext)

        when:
        def control = nodeVisitorStub.visitUnionTypeDefinition(unionTypeDefinition, context)
        then:
        1 * nodeVisitorStub.visitTypeDefinition(unionTypeDefinition, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        when:
        control = nodeVisitorStub.visitInputObjectTypeDefinition(inputObjectTypeDefinition, context)
        then:
        1 * nodeVisitorStub.visitTypeDefinition(inputObjectTypeDefinition, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        when:
        control = nodeVisitorStub.visitInputObjectTypeDefinition(inputObjectTypeDefinition, context)
        then:
        1 * nodeVisitorStub.visitTypeDefinition(inputObjectTypeDefinition, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        when:
        control = nodeVisitorStub.visitScalarTypeDefinition(scalarTypeDefinition, context)
        then:
        1 * nodeVisitorStub.visitTypeDefinition(scalarTypeDefinition, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        when:
        control = nodeVisitorStub.visitInterfaceTypeDefinition(interfaceTypeDefinition, context)
        then:
        1 * nodeVisitorStub.visitTypeDefinition(interfaceTypeDefinition, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        when:
        control = nodeVisitorStub.visitEnumTypeDefinition(enumTypeDefinition, context)
        then:
        1 * nodeVisitorStub.visitEnumTypeDefinition(enumTypeDefinition, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        when:
        control = nodeVisitorStub.visitObjectTypeDefinition(objectTypeDefinition, context)
        then:
        1 * nodeVisitorStub.visitObjectTypeDefinition(objectTypeDefinition, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT
    }

    def "types call visitTypes by default"() {
        given:
        NodeVisitorStub nodeVisitorStub = Spy(NodeVisitorStub, constructorArgs: [])
        NonNullType nonNullType = new NonNullType()
        ListType listType = new ListType()
        TypeName typeName = new TypeName("")
        TraverserContext context = Mock(TraverserContext)

        when:
        def control = nodeVisitorStub.visitNonNullType(nonNullType, context)
        then:
        1 * nodeVisitorStub.visitType(nonNullType, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        when:
        control = nodeVisitorStub.visitListType(listType, context)
        then:
        1 * nodeVisitorStub.visitType(listType, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        when:
        control = nodeVisitorStub.visitTypeName(typeName, context)
        then:
        1 * nodeVisitorStub.visitType(typeName, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT
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
