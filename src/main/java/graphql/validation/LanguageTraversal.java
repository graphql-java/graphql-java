package graphql.validation;


import graphql.language.Document;
import graphql.language.Node;

public class LanguageTraversal {


    public void traverse(Document document, QueryLanguageVisitor queryLanguageVisitor) {
        traverseImpl(document, queryLanguageVisitor);
    }


    private void traverseImpl(Node root, QueryLanguageVisitor queryLanguageVisitor) {
        queryLanguageVisitor.enter(root);
        for (Node child : root.getChildren()) {
            traverseImpl(child, queryLanguageVisitor);
        }
        queryLanguageVisitor.leave(root);

    }
}
