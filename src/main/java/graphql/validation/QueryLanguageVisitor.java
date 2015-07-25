package graphql.validation;


import graphql.language.Node;

import java.util.List;

public interface QueryLanguageVisitor {

    void enter(Node node, List<Node> path);

    void leave(Node node, List<Node> path);
}
