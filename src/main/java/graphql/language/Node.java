package graphql.language;


import java.util.List;

public interface Node<T extends Node> {

    /**
     * @return a list of the children of this node
     */
    List<Node> getChildren();

    /**
     * @return the source location where this node occurs
     */
    SourceLocation getSourceLocation();

    /**
     * Nodes can have comments made on them, the following is one comment per line before a node.
     *
     * @return the list of comments or an empty list of there are none
     */
    List<Comment> getComments();

    /**
     * Compares just the content and not the children.
     *
     * @param node the other node to compare to
     *
     * @return isEqualTo
     */
    boolean isEqualTo(Node node);

    /**
     * @return a deep copy of this node
     */
    T deepCopy();
}
