package graphql.execution.preparsed;

import graphql.GraphQLError;
import graphql.language.Document;

import java.util.Collections;
import java.util.List;

/**
 * An instance of a preparsed doucument entry represents the result of a query parse and validation, like
 * an either implementation it contains either the correct result in th document property or the errors.
 */
public class PreparsedDocumentEntry {
    private Document document;
    private List<? extends GraphQLError> errors;

    public PreparsedDocumentEntry(Document document) {
        this.document = document;
    }

    public PreparsedDocumentEntry(List<? extends GraphQLError> errors) {
        this.errors = errors;
    }

    public PreparsedDocumentEntry(GraphQLError error) {
        this.errors = Collections.singletonList(error);
    }

    public Document getDocument() {
        return document;
    }

    public List<? extends GraphQLError> getErrors() {
        return errors;
    }


    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
}
