package graphql.util

import spock.lang.Specification

class TreeTransformerUtilTest extends Specification {

    def "deleteNode adds delete zipper to shared context"() {
        given:
        def context = Mock(TraverserContext)
        def nodeZipper = Mock(NodeZipper)
        def zipperQueue = Mock(Queue)

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

    def "insertBefore adds zipper to queue"() {
        given:
        def context = Mock(TraverserContext)
        def zipper = Mock(NodeZipper)
        def zipperQueue = Mock(Queue)
        def toInsert = "insert-me"

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
