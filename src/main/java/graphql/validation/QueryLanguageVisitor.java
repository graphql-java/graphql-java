package graphql.validation;


import graphql.language.Node;

public interface QueryLanguageVisitor {

    void enter(Node node);

    void leave(Node node);
}
