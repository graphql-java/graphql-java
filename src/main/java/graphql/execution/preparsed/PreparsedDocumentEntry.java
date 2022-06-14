package graphql.execution.preparsed;

import graphql.GraphQLError;
import graphql.PublicApi;
import graphql.language.Document;

import java.io.Serializable;
import java.util.List;

import static graphql.Assert.assertNotNull;
import static java.util.Collections.singletonList;

/**
 * An instance of a preparsed document entry represents the result of a query parse and validation. In the case of
 * failed parsing or no validation errors this class acts as an either implementation. In the case of successful
 * parsing and failed validation this class provides both the document and the validation errors.
 *
 * NOTE: This class implements {@link java.io.Serializable} and hence it can be serialised and placed into a distributed cache.  However we
 * are not aiming to provide long term compatibility and do not intend for you to place this serialised data into permanent storage,
 * with times frames that cross graphql-java versions.  While we don't change things unnecessarily,  we may inadvertently break
 * the serialised compatibility across versions.
 */
@PublicApi
public class PreparsedDocumentEntry implements Serializable {
    private final Document document;
    private final List<? extends GraphQLError> errors;

    public PreparsedDocumentEntry(Document document,
                                  List<? extends GraphQLError> errors) {
        assertNotNull(document);
        assertNotNull(errors);
        this.document = document;
        this.errors = errors;
    }

    public PreparsedDocumentEntry(Document document) {
        assertNotNull(document);
        this.document = document;
        this.errors = null;
    }

    public PreparsedDocumentEntry(GraphQLError error) {
        assertNotNull(error);
        this.document = null;
        this.errors = singletonList(error);
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
