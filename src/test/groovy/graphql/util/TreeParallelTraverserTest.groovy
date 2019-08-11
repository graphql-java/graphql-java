package graphql.util

import spock.lang.Specification

import java.util.concurrent.ConcurrentLinkedQueue

class TreeParallelTraverserTest extends Specification {
    /**
     *          0
     *       1      2
     *       3    4  5
     */
    def root = [
            [number: 0, children: [
                    [number: 1, children: [
                            [number: 3, children: []]
                    ]],
                    [number: 2, children: [
                            [number: 4, children: []],
                            [number: 5, children: []]
                    ]]]
            ]
    ]


    def "test parallel traversing"() {
        given:
        Queue nodes = new ConcurrentLinkedQueue()
        def visitor = [
                enter: { TraverserContext context ->
                    def number = context.thisNode().number
                    if (number == 1) {
                        Thread.sleep(100)
                    }
                    nodes.add(number)
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        TreeParallelTraverser.parallelTraverser({ n -> n.children }).traverse(root, visitor)


        then:
        new ArrayList(nodes) == [0, 2, 4, 5, 1, 3]
        true
    }

}
