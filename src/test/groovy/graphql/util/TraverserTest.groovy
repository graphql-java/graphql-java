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
                    context.setAccumulate(context.thisNode())
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        def result = Traverser.depthFirst({ n -> n.children }).traverse(root, visitor)


        then:
        result.accumulatedResult.number == 0
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
                    context.setAccumulate(context.thisNode())
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
        result.accumulatedResult.number == 5
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
                    context.getSharedContextData().add(context.thisNode().number)
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    context.setAccumulate(leaveResult)
                    leaveCount++
                    TraversalControl.QUIT
                },

        ] as TraverserVisitor

        when:
        def result = Traverser.depthFirst({ n -> n.children }, initialData).traverse(root, visitor)

        then:
        result.accumulatedResult == leaveResult
        initialData == [0, 1, 3]
        leaveCount == 1
    }

    def "abort subtree traversal depth-first"() {
        given:
        def initialData = new ArrayList()

        def visitor = [
                enter: { TraverserContext context ->
                    context.getSharedContextData().add(context.thisNode().number)
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
                    context.getSharedContextData().add(context.thisNode().number)
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
        def result = Traverser.breadthFirst({ n -> n.children })
                .rootVars([(Object.class): "var1", (String.class): "var2"])
                .traverse(root, visitor)


        then:
        true
    }

    def "test context variables from parents"() {
        given:
        def visitor = [
                enter: { TraverserContext context ->
                    assert context.getVarFromParents(Object.class) == "var1"
                    assert context.getVarFromParents(String.class) == "var2"
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        Traverser.breadthFirst({ n -> n.children })
                .rootVars([(Object.class): "var1", (String.class): "var2"])
                .traverse(root, visitor)


        then:
        true
    }


    def "test accumulator"() {
        given:
        def visitor = [
                enter: { TraverserContext context ->
                    List visited = context.getCurrentAccumulate()
                    visited = visited == null ? new ArrayList<>() : new ArrayList<>(visited)
                    visited.add(context.thisNode().number)
                    context.setAccumulate(visited)
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        def result = Traverser.breadthFirst({ n -> n.children })
                .traverse(root, visitor)


        then:
        result.accumulatedResult == [0, 1, 2, 3, 4, 5]
    }

    def "test accumulate with initial value unchanged"() {
        given:
        def visitor = [
                enter: { TraverserContext context ->
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        def result = Traverser.breadthFirst({ n -> n.children }, null, "acc-result")
                .traverse(root, visitor)


        then:
        result.accumulatedResult == "acc-result"

    }

    def "test shared data"() {
        def visitor = [
                enter: { TraverserContext context ->
                    assert context.getSharedContextData() == "foo"
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    assert context.getSharedContextData() == "foo"
                    TraversalControl.QUIT
                },

        ] as TraverserVisitor

        when:
        Traverser.depthFirst({ n -> n.children }, "foo").traverse(root, visitor)

        then:
        true

    }

    def "test traversal with zero roots"() {

        def visitor = [] as TraverserVisitor
        def roots = []

        when:
        TraverserResult result = Traverser.depthFirst({ n -> n.children }).traverse(roots, visitor)

        then:
        result.getAccumulatedResult() == null

    }

    def "test node position"() {
        given:
        def visitor = [
                enter: { TraverserContext context ->
                    def curAcc = context.getCurrentAccumulate()
                    if (context.getLocation() != null) {
                        curAcc.add(context.getLocation().index)
                    }
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        def result = Traverser.depthFirst({ n -> n.children }, null, [])
                .traverse(root, visitor)


        then:
        result.accumulatedResult == [0, 0, 1, 0, 1]
    }

    def treeWithNamedChildren() {
        def leaf1 = [number: 3, children: [:]]
        def leaf2 = [number: 4, children: [:]]
        def leaf3 = [number: 5, children: [:]]
        def leaf4 = [number: 6, children: [:]]

        def node1 = [number: 1, children: [a: [leaf1], b: [leaf2]]]
        def node2 = [number: 2, children: [c: [leaf3], d: [leaf4]]]

        [number: 0, children: [x: [node1], y: [node2]]]
    }

    def "breadth first with named children"() {
        def root = treeWithNamedChildren()
        given:
        def visitor = [
                enter: { TraverserContext context ->
                    def curAcc = context.getCurrentAccumulate()
                    if (context.getLocation() != null) {
                        curAcc.add(context.getLocation())
                    }
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        def result = Traverser.breadthFirstWithNamedChildren({ n -> n.children }, null, [])
                .traverse(root, visitor)


        then:
        result.accumulatedResult == [new NodeLocation("x", 0),
                                     new NodeLocation("y", 0),
                                     new NodeLocation("a", 0),
                                     new NodeLocation("b", 0),
                                     new NodeLocation("c", 0),
                                     new NodeLocation("d", 0),
        ]
    }

    def "depth first with named children"() {
        def root = treeWithNamedChildren()
        given:
        def visitor = [
                enter: { TraverserContext context ->
                    def curAcc = context.getCurrentAccumulate()
                    if (context.getLocation() != null) {
                        curAcc.add(context.getLocation())
                    }
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        def result = Traverser.depthFirstWithNamedChildren({ n -> n.children }, null, [])
                .traverse(root, visitor)


        then:
        result.accumulatedResult == [new NodeLocation("y", 0),
                                     new NodeLocation("d", 0),
                                     new NodeLocation("c", 0),
                                     new NodeLocation("x", 0),
                                     new NodeLocation("b", 0),
                                     new NodeLocation("a", 0),
        ]
    }

    def "test parent nodes"() {
        given:
        def visitor = [
                enter: { TraverserContext context ->
                    context.getSharedContextData() << context.getParentNodes().collect { it.number }
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        def list = []
        when:
        Traverser.depthFirst({ n -> n.children }, list).traverse(root, visitor)


        then:
        list == [[], [0], [1, 0], [0], [2, 0], [2, 0]]

    }


    def "changing the node while traversing"() {
        given:
        def visitedNodes = []
        def visitedNodesLeave = []
        def root = [
                number: 0
        ]
        def visitor = [
                enter: { TraverserContext context ->
                    visitedNodes << context.thisNode().number
                    if (context.thisNode().number > 0) return TraversalControl.CONTINUE
                    def newRoot = [
                            number  : 0,
                            children: [
                                    [number: 1, children: []],
                                    [number: 2, children: []]
                            ]
                    ]
                    context.changeNode(newRoot)
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    visitedNodesLeave << context.thisNode().number
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        Traverser.depthFirst({ n -> n.children }).traverse(root, visitor)


        then:
        visitedNodes == [0, 1, 2]
        visitedNodesLeave == [1, 2, 0]


    }

    def "depth-first traversal children contexts are available"() {
        given:
        def childContextVars = []
        def visitor = [
                enter: { TraverserContext context ->
                    context.setVar(Object.class, context.thisNode().number)
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    def childNumbers = context.getChildrenContexts().get(null).collect { it.getVar(Object.class) }
                    childContextVars << childNumbers
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        Traverser.depthFirst({ n -> n.children }).traverse(root, visitor)


        then:
        childContextVars == [[], [3], [], [], [4, 5], [1, 2]]
    }

    def "breadth-first traversal children contexts are available"() {
        given:
        def childContextVars = []
        def visitor = [
                enter: { TraverserContext context ->
                    context.setVar(Object.class, context.thisNode().number)
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    def childNumbers = context.getChildrenContexts().get(null).collect { it.getVar(Object.class) }
                    childContextVars << childNumbers
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        Traverser.breadthFirst({ n -> n.children }).traverse(root, visitor)


        then:
        childContextVars == [[1, 2], [3], [4, 5], [], [], []]
    }

    def "delete node depth-first"() {
        given:
        def preOrderNodes = []
        def postOrderNodes = []
        def visitor = [
                enter: { TraverserContext context ->
                    preOrderNodes << context.thisNode().number
                    if (context.thisNode().number == 2) {
                        context.deleteNode()
                    }
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    postOrderNodes << context.originalThisNode().number
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        def result = Traverser.depthFirst({ n -> n.children }).traverse(root, visitor)


        then:
        preOrderNodes == [0, 1, 3, 2]
        postOrderNodes == [3, 1, 2, 0]
    }

    def "delete nodes breadth-first"() {
        given:
        def enterData = []
        def leaveData = []
        def visitor = [
                enter: { TraverserContext context ->
                    enterData << context.thisNode().number
                    if (context.thisNode().number == 2) {
                        context.deleteNode()
                    }
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    leaveData << context.originalThisNode().number
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        def result = Traverser.breadthFirst({ n -> n.children }).traverse(root, visitor)

        then:
        enterData == [0, 1, 2, 3]
        leaveData == [0, 1, 2, 3]
    }

}



