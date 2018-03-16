package graphql.language

import graphql.util.TraversalControl
import graphql.util.TraverserContext
import spock.lang.Specification

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


}
