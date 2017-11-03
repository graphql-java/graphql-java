package graphql.validation;


import graphql.Internal;
import graphql.language.Node;
import graphql.schema.visibility.GraphqlFieldVisibilityEnvironment;

import java.util.ArrayList;
import java.util.List;

import static graphql.schema.visibility.GraphqlFieldVisibilityEnvironment.newEnvironment;

@Internal
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

    public void traverse(Node root, DocumentVisitor documentVisitor, GraphqlFieldVisibilityEnvironment fieldVisibilityEnvironment) {
        traverseImpl(root, documentVisitor, path, fieldVisibilityEnvironment);
    }


    private void traverseImpl(Node root, DocumentVisitor documentVisitor, List<Node> path, GraphqlFieldVisibilityEnvironment fieldVisibilityEnvironment) {
        documentVisitor.enter(root, path, fieldVisibilityEnvironment);
        path.add(root);
        for (Node child : root.getChildren()) {
            traverseImpl(child, documentVisitor, path, fieldVisibilityEnvironment);
        }
        path.remove(path.size() - 1);
        documentVisitor.leave(root, path, fieldVisibilityEnvironment);
    }
}
