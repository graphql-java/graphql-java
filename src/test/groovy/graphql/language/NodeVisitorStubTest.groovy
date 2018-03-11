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

    // TODO: more testing
}
