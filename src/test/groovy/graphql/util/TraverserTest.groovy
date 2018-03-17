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
                    println "enter:$preOrderNodes"
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    postOrderNodes << context.thisNode().number
                    println "leave:$postOrderNodes"
                    context.setResult(context.thisNode())
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        def result = Traverser.depthFirst({ n -> n.children }).traverse(root, visitor)


        then:
        result.result.number == 0
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
                    context.setResult(context.thisNode())
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
        def result = Traverser.breadthFirst({ n -> n.children }).traverse(root, visitor)

        then:
        result.result.number == 5
        enterData == [0, 1, 2, 3, 4, 5]
        leaveData == [0, 1, 2, 3, 4, 5]
    }

    def "quit traversal immediately"() {
        given:
        def enterData = []

        def visitor = [
                enter: { TraverserContext context ->
                    enterData << context.thisNode().number
                    TraversalControl.QUIT
                }
        ] as TraverserVisitor

        when:
        def result = Traverser.breadthFirst({ n -> n.children }).traverse(root, visitor)

        then:
        enterData == [0]


        when:
        enterData = []
        result = Traverser.depthFirst({ n -> n.children }).traverse(root, visitor)

        then:
        enterData == [0]

    }

    def "quit traversal in first leave"() {
        given:
        def initialData = new ArrayList()
        def leaveResult = "Leave result"
        def leaveCount = 0
        def visitor = [
                enter: { TraverserContext context ->
                    context.getInitialData().add(context.thisNode().number)
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    context.setResult(leaveResult)
                    leaveCount++
                    TraversalControl.QUIT
                },

        ] as TraverserVisitor

        when:
        def result = Traverser.depthFirst({ n -> n.children }, initialData).traverse(root, visitor)

        then:
        result.result == leaveResult
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
        def result = Traverser.depthFirst({ n -> n.children }, initialData).traverse(root, visitor)

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
        def result = Traverser.breadthFirst({ n -> n.children }, initialData).traverse(root, visitor)

        then:
        initialData == [0, 1, 2, 3]
    }

    static class Node {
        int number
        List<Node> children = new ArrayList<>()
    }

    def "simple cycle"() {
        given:
        def cycleRoot = new Node(number: 0)
        cycleRoot.children.add(cycleRoot)

        def visitor = Mock(TraverserVisitor)
        when:
        def result = Traverser.depthFirst({ n -> n.children }).traverse(cycleRoot, visitor)

        then:
        1 * visitor.enter(_) >> TraversalControl.CONTINUE
        1 * visitor.leave(_) >> TraversalControl.CONTINUE
        1 * visitor.backRef({ TraverserContext context -> context.thisNode() == cycleRoot }) >> TraversalControl.CONTINUE
    }

    def "more complex cycles"() {
        given:
        def cycleRoot = new Node(number: 0)
        cycleRoot.children.add(new Node(number: 1))

        def node2 = new Node(number: 2)
        cycleRoot.children.add(node2)
        node2.children.add(node2)

        def node3 = new Node(number: 3)
        cycleRoot.children.add(node3)
        def node4 = new Node(number: 4)
        node3.children.add(node4)
        node4.children.add(cycleRoot)

        def visitor = Mock(TraverserVisitor)
        when:
        def result = Traverser.depthFirst({ n -> n.children }).traverse(cycleRoot, visitor)

        then:
        5 * visitor.enter(_) >> TraversalControl.CONTINUE
        5 * visitor.leave(_) >> TraversalControl.CONTINUE
        1 * visitor.backRef({ TraverserContext context -> context.thisNode() == cycleRoot }) >> TraversalControl.CONTINUE
        1 * visitor.backRef({ TraverserContext context -> context.thisNode() == node2 }) >> TraversalControl.CONTINUE
        0 * visitor.backRef(_)
    }

    def "abort when cycle found"() {
        def cycleRoot = new Node(number: 0)
        cycleRoot.children.add(new Node(number: 1))

        def node2 = new Node(number: 2)
        cycleRoot.children.add(node2)

        def node3 = new Node(number: 3)
        cycleRoot.children.add(node3)
        def node4 = new Node(number: 4)
        node3.children.add(node4)
        node4.children.add(cycleRoot)
        def visitor = Mock(TraverserVisitor)

        when:
        def result = Traverser.depthFirst({ n -> n.children }).traverse(cycleRoot, visitor)

        then:
        5 * visitor.enter(_) >> TraversalControl.CONTINUE
        2 * visitor.leave(_) >> TraversalControl.CONTINUE
        1 * visitor.backRef({ TraverserContext context -> context.thisNode() == cycleRoot }) >> TraversalControl.QUIT
        0 * visitor.backRef(_)
    }


    def "test context variables"() {
        given:
        def visitor = [
                enter: { TraverserContext context ->
                    assert context.getParentContext().getVar(Object.class) == "var1"
                    assert context.getParentContext().getVar(String.class) == "var2"
                    context.setVar(Object.class, "var1")
                    context.setVar(String.class, "var2")

                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        def result = Traverser.breadthFirst({ n -> n.children },)
                .rootVars([(Object.class): "var1", (String.class): "var2"])
                .traverse(root, visitor)


        then:
        true
    }

    def "test parent result chain"() {
        given:
        def visitor = [
                enter: { TraverserContext context ->
                    List visited = context.getParentResult()
                    visited = visited == null ? new ArrayList<>() : visited
                    visited.add(context.thisNode().number)
                    context.setVar(List.class, visited)
                    context.setResult(visited)
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        def result = Traverser.breadthFirst({ n -> n.children },)
                .traverse(root, visitor)


        then:
        result.result == [0, 1, 2, 3, 4, 5]
    }

    def "test initial data"() {
        def visitor = [
                enter: { TraverserContext context ->
                    assert context.getInitialData() == "foo"
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    assert context.getInitialData() == "foo"
                    TraversalControl.QUIT
                },

        ] as TraverserVisitor

        when:
        Traverser.depthFirst({ n -> n.children }, "foo").traverse(root, visitor)

        then:
        true

    }

}



