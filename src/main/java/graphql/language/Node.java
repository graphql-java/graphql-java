package graphql.language;


import java.util.List;

public interface Node {

    List<Node> getChildren();

    SourceLocation getSourceLocation();

    /**
     * Nodes can have comments made on them
     *
     * @return the list of comments or an empty list of there are none
     */
    List<String> getComments();

    /**
     * Compares just the content and not the children.
     *
     * @param node the other node to compare to
     * @return isEqualTo
     */
    boolean isEqualTo(Node node);
}
