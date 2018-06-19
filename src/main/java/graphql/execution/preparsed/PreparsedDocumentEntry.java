package graphql.execution.preparsed;

import graphql.GraphQLError;
import graphql.language.Document;

import java.util.List;

import static graphql.Assert.assertNotNull;
import static java.util.Collections.singletonList;

/**
 * An instance of a preparsed document entry represents the result of a query parse and validation, like
 * an either implementation it contains either the correct result in th document property or the errors.
 */
public class PreparsedDocumentEntry {
    private final Document document;
    private final List<? extends GraphQLError> errors;

    public PreparsedDocumentEntry(Document document) {
        assertNotNull(document);
        this.document = document;
        this.errors = null;
    }

    public PreparsedDocumentEntry(List<? extends GraphQLError> errors) {
        assertNotNull(errors);
        this.document = null;
        this.errors = errors;
    }

    public PreparsedDocumentEntry(GraphQLError error) {
        this(singletonList(assertNotNull(error)));
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
