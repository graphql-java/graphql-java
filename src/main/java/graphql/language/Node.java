package graphql.language;


import java.util.List;

/**
 * <p>Node interface.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public interface Node {

    /**
     * <p>getChildren.</p>
     *
     * @return a {@link java.util.List} object.
     */
    List<Node> getChildren();

    /**
     * <p>getSourceLocation.</p>
     *
     * @return a {@link graphql.language.SourceLocation} object.
     */
    SourceLocation getSourceLocation();

    /**
     * Compares just the content and not the children.
     *
     * @param node a {@link graphql.language.Node} object.
     * @return a boolean.
     */
    boolean isEqualTo(Node node);
}
