package graphql.util

import spock.lang.Specification

class TraverserTest extends Specification {

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

    def "test depth-first traversal"() {
        given:
        def preOrderNodes = []
        def postOrderNodes = []
        def visitor = [
                enter: { TraverserContext context ->
                    preOrderNodes << context.thisNode().number
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    postOrderNodes << context.thisNode().number
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        Traverser.depthFirst({ n -> n.children }).traverse(root, visitor)


        then:
        preOrderNodes == [0, 1, 3, 2, 4, 5]
        postOrderNodes == [3, 1, 4, 5, 2, 0]
    }


    def "test breadth-first traversal"() {
        given:
        def enterData = []
        def leaveData = []
        def visitor = [
                enter: { TraverserContext context ->
                    enterData << context.thisNode().number
                    println "enter:$enterData"
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    leaveData << context.thisNode().number
                    println "leave:$leaveData"
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        Traverser.breadthFirst({ n -> n.children }).traverse(root, visitor)

        then:
        enterData == [0, 1, 2, 3, 4, 5]
        leaveData == [0, 1, 2, 3, 4, 5]
    }

    def "quit traversal immediately"() {
        given:
        def initialData = new ArrayList()

        def visitor = [
                enter: { TraverserContext context ->
                    context.getInitialData().add(context.thisNode().number)
                    TraversalControl.QUIT
                }
        ] as TraverserVisitor

        when:
        Traverser.breadthFirst({ n -> n.children }, initialData).traverse(root, visitor)

        then:
        initialData == [0]


        when:
        initialData.clear()
        Traverser.depthFirst({ n -> n.children }, initialData).traverse(root, visitor)

        then:
        initialData == [0]

    }

    def "quit traversal in first leave"() {
        given:
        def initialData = new ArrayList()
        def leaveCount = 0

        def visitor = [
                enter: { TraverserContext context ->
                    context.getInitialData().add(context.thisNode().number)
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    leaveCount++
                    TraversalControl.QUIT
                },

        ] as TraverserVisitor

        when:
        Traverser.depthFirst({ n -> n.children }, initialData).traverse(root, visitor)

        then:
        initialData == [0, 1, 3]
        leaveCount == 1

    }

    def "abort subtree traversal depth-first"() {
        given:
        def initialData = new ArrayList()

        def visitor = [
                enter: { TraverserContext context ->
                    context.getInitialData().add(context.thisNode().number)
                    if ([1, 2].contains(context.thisNode().number)) return TraversalControl.ABORT
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    TraversalControl.CONTINUE
                },

        ] as TraverserVisitor

        when:
        Traverser.depthFirst({ n -> n.children }, initialData).traverse(root, visitor)

        then:
        initialData == [0, 1, 2]

    }

    def "abort subtree traversal breadth-first"() {
        given:
        def initialData = new ArrayList()

        def visitor = [
                enter: { TraverserContext context ->
                    context.getInitialData().add(context.thisNode().number)
                    if ([2].contains(context.thisNode().number)) return TraversalControl.ABORT
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    TraversalControl.CONTINUE
                },

        ] as TraverserVisitor

        when:
        Traverser.breadthFirst({ n -> n.children }, initialData).traverse(root, visitor)

        then:
        initialData == [0, 1, 2, 3]

    }

}

