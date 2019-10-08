package graphql.util

import spock.lang.Specification

import java.util.concurrent.ConcurrentLinkedQueue

import static org.awaitility.Awaitility.await

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
                    println "number: $number"
                    if (number == 1) {
                        await().until({ nodes.size() == 4 })
                    }
                    nodes.add(number)
                    println "added new node: $nodes"
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
