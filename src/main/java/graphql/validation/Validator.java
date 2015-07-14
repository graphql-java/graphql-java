package graphql.validation;


import graphql.language.Document;
import graphql.language.Node;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.List;

public class Validator {

    public List<ValidationError> validateDocument(GraphQLSchema schema, Document document) {
        List<ValidationError> result = new ArrayList<>();
        LanguageTraversal languageTraversal= new LanguageTraversal();
        languageTraversal.traverse(document, new QueryLanguageVisitor() {
            @Override
            public void enter(Node node) {
                System.out.println("enter" + node);
            }

            @Override
            public void leave(Node node) {
                System.out.println("leave" + node);
            }
        });


        return result;
    }
}
