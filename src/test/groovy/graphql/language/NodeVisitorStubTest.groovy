package graphql.language

import graphql.util.TraversalControl
import graphql.util.TraverserContext
import spock.lang.Specification
import spock.lang.Unroll

import static java.util.Collections.emptyList

class NodeVisitorStubTest extends Specification {


    def "selection nodes call visitSelection by default"() {
        given:
        NodeVisitorStub nodeVisitorStub = Spy(NodeVisitorStub, constructorArgs: [])
        Field field = new Field()
        FragmentSpread fragmentSpread = new FragmentSpread()
        InlineFragment inlineFragment = new InlineFragment()
        TraverserContext context = Mock(TraverserContext)

        when:
        def control = nodeVisitorStub.visitField(field, context)

        then:
        1 * nodeVisitorStub.visitSelection(field, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        when:
        control = nodeVisitorStub.visitInlineFragment(inlineFragment, context)

        then:
        1 * nodeVisitorStub.visitSelection(inlineFragment, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        when:
        control = nodeVisitorStub.visitFragmentSpread(fragmentSpread, context)

        then:
        1 * nodeVisitorStub.visitSelection(fragmentSpread, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT
    }

    def "values nodes call visitValue by default"() {
        given:
        NodeVisitorStub nodeVisitorStub = Spy(NodeVisitorStub, constructorArgs: [])
        FloatValue floatValue = new FloatValue()
        ArrayValue arrayValue = new ArrayValue()
        IntValue intValue = new IntValue()
        BooleanValue booleanValue = new BooleanValue(true)
        NullValue nullValue = NullValue.Null
        ObjectValue objectValue = new ObjectValue()
        VariableReference variableReference = new VariableReference()
        EnumValue enumValue = new EnumValue()
        StringValue stringValue = new StringValue()
        TraverserContext context = Mock(TraverserContext)

        when:
        def control = nodeVisitorStub.visitFloatValue(floatValue, context)
        then:
        1 * nodeVisitorStub.visitValue(floatValue, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        when:
        control = nodeVisitorStub.visitArrayValue(arrayValue, context)
        then:
        1 * nodeVisitorStub.visitValue(arrayValue, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        when:
        control = nodeVisitorStub.visitIntValue(intValue, context)
        then:
        1 * nodeVisitorStub.visitValue(intValue, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        when:
        control = nodeVisitorStub.visitBooleanValue(booleanValue, context)
        then:
        1 * nodeVisitorStub.visitValue(booleanValue, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        when:
        control = nodeVisitorStub.visitNullValue(nullValue, context)
        then:
        1 * nodeVisitorStub.visitValue(nullValue, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        when:
        control = nodeVisitorStub.visitObjectValue(objectValue, context)
        then:
        1 * nodeVisitorStub.visitValue(objectValue, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        when:
        control = nodeVisitorStub.visitVariableReference(variableReference, context)
        then:
        1 * nodeVisitorStub.visitValue(variableReference, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        when:
        control = nodeVisitorStub.visitEnumValue(enumValue, context)
        then:
        1 * nodeVisitorStub.visitValue(enumValue, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        when:
        control = nodeVisitorStub.visitStringValue(stringValue, context)
        then:
        1 * nodeVisitorStub.visitStringValue(stringValue, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT
    }

    def "definitions call visitDefinition by default"() {
        given:
        NodeVisitorStub nodeVisitorStub = Spy(NodeVisitorStub, constructorArgs: [])
        OperationDefinition operationDefinition = new OperationDefinition()
        FragmentDefinition fragmentDefinition = new FragmentDefinition()
        DirectiveDefinition directiveDefinition = new DirectiveDefinition("")
        SchemaDefinition schemaDefinition = new SchemaDefinition()
        TraverserContext context = Mock(TraverserContext)

        when:
        def control = nodeVisitorStub.visitOperationDefinition(operationDefinition, context)
        then:
        1 * nodeVisitorStub.visitDefinition(operationDefinition, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        when:
        control = nodeVisitorStub.visitFragmentDefinition(fragmentDefinition, context)
        then:
        1 * nodeVisitorStub.visitDefinition(fragmentDefinition, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        when:
        control = nodeVisitorStub.visitDirectiveDefinition(directiveDefinition, context)
        then:
        1 * nodeVisitorStub.visitDefinition(directiveDefinition, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        when:
        control = nodeVisitorStub.visitSchemaDefinition(schemaDefinition, context)
        then:
        1 * nodeVisitorStub.visitDefinition(schemaDefinition, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT
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
