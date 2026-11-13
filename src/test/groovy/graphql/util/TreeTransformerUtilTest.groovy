package graphql.util

import spock.lang.Specification

class TreeTransformerUtilTest extends Specification {

    def "changeNode in parallel mode with already changed node"() {
        given:
        def context = Mock(TraverserContext)
        def zippers = []
        def adapter = Mock(NodeAdapter)
        def originalNode = "original"
        def newNode = "changed"

        def mockZipper = Mock(NodeZipper)
        mockZipper.getCurNode() >> originalNode
        zippers << mockZipper

        context.isParallel() >> true
        context.isChanged() >> true
        context.thisNode() >> originalNode
        context.getVar(List) >> zippers
        context.getVar(NodeAdapter) >> adapter

        when:
        def result = TreeTransformerUtil.changeNode(context, newNode)

        then:
        1 * context.changeNode(newNode)
        result == TraversalControl.CONTINUE
    }

    def "deleteNode in sequential mode adds delete zipper to shared context"() {
        given:
        def context = Mock(TraverserContext)
        def nodeZipper = Mock(NodeZipper)
        def zipperQueue = Mock(Queue)

        context.isParallel() >> false
        context.getVar(NodeZipper) >> nodeZipper
        context.getSharedContextData() >> zipperQueue
        nodeZipper.deleteNode() >> nodeZipper

        when:
        def result = TreeTransformerUtil.deleteNode(context)

        then:
        1 * context.deleteNode()
        1 * zipperQueue.add(nodeZipper)
        result == TraversalControl.CONTINUE
    }

    def "insertBefore in sequential mode adds zipper to queue"() {
        given:
        def context = Mock(TraverserContext)
        def zipper = Mock(NodeZipper)
        def zipperQueue = Mock(Queue)
        def toInsert = "insert-me"

        context.isParallel() >> false
        context.getVar(NodeZipper) >> zipper
        zipper.insertBefore(toInsert) >> zipper
        context.getSharedContextData() >> zipperQueue

        when:
        def result = TreeTransformerUtil.insertBefore(context, toInsert)

        then:
        1 * zipperQueue.add(zipper)
        result == TraversalControl.CONTINUE
    }
}
