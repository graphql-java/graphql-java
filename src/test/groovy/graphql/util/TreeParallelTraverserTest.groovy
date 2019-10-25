package graphql.util

import spock.lang.Ignore
import spock.lang.Specification

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ForkJoinPool
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


    // fails on travis ci because only one thread is used for an unknown reason
    @Ignore
    def "test parallel traversing"() {
        given:
        Queue nodes = new ConcurrentLinkedQueue()
        CountDownLatch latch = new CountDownLatch(4)
        def visitor = [
                enter: { TraverserContext context ->
                    def number = context.thisNode().number
                    println "number: $number in ${Thread.currentThread()}"
                    if (number == 1) {
                        println "wating in ${Thread.currentThread()}"
                        assert latch.await(30, TimeUnit.SECONDS)
                    }
                    nodes.add(number)
                    latch.countDown()
                    println "added new node: $nodes with size: ${nodes.size()} and latch: ${latch.getCount()} in ${Thread.currentThread()}"
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        def pool = new ForkJoinPool(4)
        println "parellism: ${pool.getParallelism()}"
        println "processors: ${Runtime.getRuntime().availableProcessors()}"
        TreeParallelTraverser.parallelTraverser({ n -> n.children }, pool).traverse(root, visitor)


        then:
        new ArrayList(nodes) == [0, 2, 4, 5, 1, 3]
        true
    }

    def "test traversing"() {
        given:
        Queue nodes = new ConcurrentLinkedQueue()
        def visitor = [
                enter: { TraverserContext context ->
                    def number = context.thisNode().number
                    nodes.add(number)
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        def pool = new ForkJoinPool(4)
        TreeParallelTraverser.parallelTraverser({ n -> n.children }, pool).traverse(root, visitor)

        then:
        new ArrayList(nodes).containsAll([0, 2, 4, 5, 1, 3])
        true
    }

}
