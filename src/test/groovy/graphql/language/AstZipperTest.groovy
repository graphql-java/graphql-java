package graphql.language

import graphql.util.Breadcrumb
import graphql.util.NodeLocation
import graphql.util.NodeZipper
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import spock.lang.Specification

import static graphql.language.AstNodeAdapter.AST_NODE_ADAPTER
import static java.util.Arrays.asList
import static java.util.Collections.emptyList

class AstZipperTest extends Specification {

    /*
     * Modify a node that is part of tree, resulting in a new tree being created with updated references. New nodes
     * are created when necessary, instead of having their attributes modified. This test exemplifies the immutable
     * characteristics of nodes.
     *
     * The node "child 1.2" will be renamed to "child 1.2x", this results in a new node being created with identical
     * characteristics to the old node, except the name, and replacing the old node in the tree.
     *
     * This modification requires that the parent nodes are also updated so they have a reference to the newly created
     * node. As a result, new instances are also created for "child 1" and "root". The rest of the tree remains
     * untouched (including the children of "child 1.2").
     *
     *
     * Original Tree:
     *
     *
     *                                   root
     *                                    +
     *                                    |
     *                                    |
     *                     +--------------+---------------+
     *                     |                              |
     *                     v                              v
     *                   child 1                       child 2
     *                     +                              +
     *                     |                              |
     *                     |                              |
     *              +------+------+                +------+------+
     *              |             |                |             |
     *              v             v                v             v
     *          child 1.1     child 1.2        child 2.1     child 2.2
     *                            +
     *                            |
     *                            v
     *                       child 1.2.1
     *
     *
     *
     *
     * Modified Tree:
     *
     *
     *                                   root'
     *                                    +
     *                                    |
     *                                    |
     *                     +--------------+---------------+
     *                     |                              |
     *                     v                              v
     *                   child 1'                      child 2
     *                     +                              +
     *                     |                              |
     *                     |                              |
     *              +------+------+                +------+------+
     *              |             |                |             |
     *              v             v                v             v
     *          child 1.1     child 1.2x'      child 2.1     child 2.2
     *                            +
     *                            |
     *                            v
     *                       child 1.2.1
     *
     *  - A new node, named "child 1.2x" is created to replace the node named "child 1.2"
     *  - A new "child 1" is created and it references the newly created "child 1.2x" node
     *  - A new "root" is created and it references the newly created "child 1" node
     *  - The rest of the tree is not modified (same object references)
     *
     */


    def 'modify a child node'() {
        def root = node("root",
                node("child 1",
                        node("child 1.1"),
                        node("child 1.2",
                                node("child 1.2.1")
                        )
                ),
                node("child 2",
                        node("child 2.1"),
                        node("child 2.2")
                )
        )

        Node child1 = root.selectChild(0)
        Node child1_2 = child1.selectChild(1)

        List<Breadcrumb> breadcrumbs = asList(
                new Breadcrumb<>(child1, new NodeLocation("children", 1)),
                new Breadcrumb<>(root, new NodeLocation("children", 0))
        )

        NodeZipper zipper = new NodeZipper(child1_2, breadcrumbs, AST_NODE_ADAPTER)

        final MyNode rootModified = (MyNode) zipper.modifyNode(
                { node ->
                    new MyNode("child 1.2x", node.getChildren())
                }
        ).toRoot()

        expect:

        rootModified.selectChild(0, 1).getValue() == "child 1.2x"

        rootModified != root

        rootModified.selectChild(0) != root.selectChild(0)
        rootModified.selectChild(0, 0) == root.selectChild(0, 0)
        rootModified.selectChild(0, 1) != root.selectChild(0, 1)
        rootModified.selectChild(0, 1, 0) == root.selectChild(0, 1, 0)

        rootModified.selectChild(1) == root.selectChild(1)
        rootModified.selectChild(1, 0) == root.selectChild(1, 0)
        rootModified.selectChild(1, 1) == root.selectChild(1, 1)
    }

    static node(String value, Node... children) {
        new MyNode(value, asList(children))
    }

    static class MyNode extends AbstractNode {
        private final String value
        private final List<Node> children

        private static final String CHILDREN = "children"

        MyNode(String value, List<Node> children) {
            super(null, emptyList(), IgnoredChars.EMPTY)
            this.value = value
            this.children = children
        }

        String getValue() {
            return value
        }

        @Override
        List<Node> getChildren() {
            return children
        }

        @Override
        boolean isEqualTo(Node node) {
            if (node instanceof MyNode) {
                return ((MyNode) node).value.equals(this.value)
            }

            return false
        }

        @Override
        MyNode deepCopy() {
            return null
        }

        @Override
        TraversalControl accept(TraverserContext context, NodeVisitor visitor) {
            return null
        }

        @Override
        NodeChildrenContainer getNamedChildren() {
            return NodeChildrenContainer.newNodeChildrenContainer().children(CHILDREN, children).build()
        }

        @Override
        Node withNewChildren(NodeChildrenContainer newChildren) {
            return new MyNode(this.getValue(), newChildren.getChildren(CHILDREN))
        }

        @Override
        String toString() {
            return value
        }

        MyNode selectChild(Integer... indexes) {
            def node = this

            for (int i = 0; i < indexes.length; i++) {
                node = node.children.get(indexes[i])
            }

            (MyNode) node
        }
    }
}

