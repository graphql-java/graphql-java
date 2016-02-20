package graphql.validation;


import graphql.language.Node;

import java.util.List;

/**
 * <p>QueryLanguageVisitor interface.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public interface QueryLanguageVisitor {

    /**
     * <p>enter.</p>
     *
     * @param node a {@link graphql.language.Node} object.
     * @param path a {@link java.util.List} object.
     */
    void enter(Node node, List<Node> path);

    /**
     * <p>leave.</p>
     *
     * @param node a {@link graphql.language.Node} object.
     * @param path a {@link java.util.List} object.
     */
    void leave(Node node, List<Node> path);
}
