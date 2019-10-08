package graphql.util

import spock.lang.Specification

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        CountDownLatch latch = new CountDownLatch(4)
        def visitor = [
                enter: { TraverserContext context ->
                    def number = context.thisNode().number
                    println "number: $number"
                    if (number == 1) {
                        println "awaiting"
                        assert latch.await(120, TimeUnit.SECONDS)
                    }
                    nodes.add(number)
                    latch.countDown()
                    println "added new node: $nodes with size: ${nodes.size()} and latch: ${latch.getCount()}"
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
