package graphql.language

import graphql.util.TraversalControl
import graphql.util.TraverserContext
import spock.lang.Specification

import static graphql.language.NodeTraverser.LeaveOrEnter.ENTER
import static graphql.language.NodeTraverser.LeaveOrEnter.LEAVE

class NodeTraverserTest extends Specification {

    def "traverse nodes in depth first"() {
        given:
        Field leaf = new Field("leaf")
        SelectionSet rootSelectionSet = new SelectionSet(Arrays.asList(leaf))
        Field root = new Field("root")
        root.setSelectionSet(rootSelectionSet)

        NodeTraverser nodeTraverser = new NodeTraverser()
        NodeVisitor nodeVisitor = Mock(NodeVisitor)
        when:
        nodeTraverser.depthFirst(nodeVisitor, root)

        then:
        1 * nodeVisitor.visitField(root, { isEnter(it) }) >> TraversalControl.CONTINUE
        then:
        1 * nodeVisitor.visitSelectionSet(rootSelectionSet, { isEnter(it) }) >> TraversalControl.CONTINUE
        then:
        1 * nodeVisitor.visitField(leaf, { isEnter(it) }) >> TraversalControl.CONTINUE
        then:
        1 * nodeVisitor.visitField(leaf, { isLeave(it) }) >> TraversalControl.CONTINUE
        then:
        1 * nodeVisitor.visitSelectionSet(rootSelectionSet, { isLeave(it) }) >> TraversalControl.CONTINUE
        then:
        1 * nodeVisitor.visitField(root, { isLeave(it) }) >> TraversalControl.CONTINUE
        then:
        0 * nodeVisitor._
    }

    def "uses root vars"() {
        given:
        Field root = new Field("root")
        def rootVars = [(String.class): "string", (Integer.class): 1]
        NodeTraverser nodeTraverser = new NodeTraverser(rootVars, { Node node -> node.getChildren() })
        NodeVisitor nodeVisitor = Mock(NodeVisitor)
        when:
        nodeTraverser.depthFirst(nodeVisitor, [root])

        then:
        2 * nodeVisitor.visitField(root, { TraverserContext context ->
            context.parentContext.getVar(String.class) == "string" &&
                    context.parentContext.getVar(Integer.class) == 1
        }) >> TraversalControl.CONTINUE

    }

    def "uses custom getChildren"() {
        given:
        Field root = new Field("root")
        Field root2 = new Field("root")
        NodeTraverser nodeTraverser = new NodeTraverser([:], { Node node -> node == root ? [root2] : [] })
        NodeVisitor nodeVisitor = Mock(NodeVisitor)
        when:
        nodeTraverser.depthFirst(nodeVisitor, [root])

        then:
        2 * nodeVisitor.visitField(root, _) >> TraversalControl.CONTINUE
        2 * nodeVisitor.visitField(root2, _) >> TraversalControl.CONTINUE

    }


    boolean isEnter(TraverserContext context) {
        return context.getVar(NodeTraverser.LeaveOrEnter.class) == ENTER
    }

    boolean isLeave(TraverserContext context) {
        return context.getVar(NodeTraverser.LeaveOrEnter.class) == LEAVE
    }
}
