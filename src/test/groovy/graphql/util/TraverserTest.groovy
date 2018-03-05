package graphql.util

import spock.lang.Specification

class TraverserTest extends Specification {

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

    def "test pre-order depth-first traversal"() {
        given:
        def initialData = []
        def visitor = [
                enter: { TraverserContext context ->
                    context.getInitialData().add(context.thisNode().number)
                    TraversalControl.CONTINUE
                },
                leave: { TraversalControl.CONTINUE }
        ] as TraverserVisitor
        when:
        Traverser.depthFirst({ n -> n.children }, initialData).traverse(root, visitor)


        then:
        initialData == [0, 1, 3, 2, 4, 5]
    }

    def "test post-order depth-first traversal"() {
        given:
        def initialData = new ArrayList();
        def visitor = [
                enter: { TraverserContext context ->
                    TraversalControl.CONTINUE
                },
                leave: { TraverserContext context ->
                    context.getInitialData().add(context.thisNode().number)
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        Traverser.depthFirst({ n -> n.children }, initialData).traverse(root, visitor)

        then:
        initialData == [3, 1, 4, 5, 2, 0]
    }

    def "test breadth-first traversal"() {
        given:
        def initialData = new ArrayList()
        def visitor = [
                enter: { TraverserContext context ->
                    context.getInitialData().add(context.thisNode().number)
                    TraversalControl.CONTINUE
                },
                leave: {
                    TraversalControl.CONTINUE
                }
        ] as TraverserVisitor
        when:
        Traverser.breadthFirst({ n -> n.children }, initialData).traverse(root, visitor)

        then:
        initialData == [0, 1, 2, 3, 4, 5]
    }

}

