package graphql.schema.idl;

import graphql.Internal;
import graphql.PublicApi;
import graphql.language.Node;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;

/**
 * This represents a hierarchy from a graphql language node upwards to its
 * associated parent nodes.  For example a Directive can be on a InputValueDefinition
 * which can be on a Argument, which can be on a FieldDefinition which may be
 * on an ObjectTypeDefinition.
 */
@PublicApi
public class NodeInfo {

    private final Node node;
    private final Optional<NodeInfo> parent;

    @Internal
    NodeInfo(Deque<Node> nodeStack) {
        assertNotNull(nodeStack, "You MUST have a non null stack of nodes");
        assertTrue(!nodeStack.isEmpty(), "You MUST have a non empty stack of nodes");

        Deque<Node> copy = new ArrayDeque<>(nodeStack);
        node = copy.pop();
        if (!copy.isEmpty()) {
            parent = Optional.of(new NodeInfo(copy));
        } else {
            parent = Optional.empty();
        }
    }

    /**
     * Returns the node represented by this info
     *
     * @param <T> of the type you want
     *
     * @return the node in play
     */
    public <T extends Node> T getNode() {
        //noinspection unchecked
        return (T) node;
    }

    /**
     * @return a node MAY have an optional parent
     */
    public Optional<NodeInfo> getParentInfo() {
        return parent;
    }

    @Override
    public String toString() {
        return String.valueOf(node) +
                " - parent : " +
                parent.isPresent();
    }
}
