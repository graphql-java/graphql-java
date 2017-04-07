package graphql.validation;


import graphql.language.Node;

import java.util.ArrayList;
import java.util.List;

public class LanguageTraversal {

    private final List<Node> path;

    public LanguageTraversal() {
        path = new ArrayList<>();
    }

    public LanguageTraversal(List<Node> basePath) {
        if (basePath != null) {
            path = basePath;
        } else {
            path = new ArrayList<>();
        }
    }

    public void traverse(Node root, QueryLanguageVisitor queryLanguageVisitor) {
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
