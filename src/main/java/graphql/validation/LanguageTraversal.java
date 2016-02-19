package graphql.validation;


import graphql.language.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>LanguageTraversal class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class LanguageTraversal {


    /**
     * <p>traverse.</p>
     *
     * @param root a {@link graphql.language.Node} object.
     * @param queryLanguageVisitor a {@link graphql.validation.QueryLanguageVisitor} object.
     */
    public void traverse(Node root, QueryLanguageVisitor queryLanguageVisitor) {
        List<Node> path = new ArrayList<>();
        traverseImpl(root, queryLanguageVisitor, path);
    }


    private void traverseImpl(Node root, QueryLanguageVisitor queryLanguageVisitor, List<Node> path) {
        queryLanguageVisitor.enter(root, path);
        path.add(root);
        for (Node child : root.getChildren()) {
            traverseImpl(child, queryLanguageVisitor, path);
        }
        path.remove(path.size() - 1);
        queryLanguageVisitor.leave(root, path);


    }
}
