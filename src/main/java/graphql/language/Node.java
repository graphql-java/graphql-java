package graphql.language;


import java.util.List;

public interface Node {

    List<Node> getChildren();

    SourceLocation getSourceLocation();

    /**
     * Compares just the content and not the children.
     *
     * @param node the other node to compare to
     * @return isEqualTo
     */
    boolean isEqualTo(Node node);
}
