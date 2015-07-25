package graphql.validation;


import graphql.language.Document;
import graphql.language.Node;

import java.util.ArrayList;
import java.util.List;

public class LanguageTraversal {


    public void traverse(Document document, QueryLanguageVisitor queryLanguageVisitor) {
        List<Node> path = new ArrayList<>();
        traverseImpl(document, queryLanguageVisitor, path);
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
