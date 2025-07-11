package graphql.normalized.nf.provider;

import graphql.GraphQLError;
import graphql.PublicApi;
import graphql.language.Document;
import graphql.normalized.nf.NormalizedDocument;

import java.io.Serializable;
import java.util.List;

import static graphql.Assert.assertNotNull;
import static java.util.Collections.singletonList;

/**
 * NOTE: This class implements {@link Serializable} and hence it can be serialised and placed into a distributed cache.  However we
 * are not aiming to provide long term compatibility and do not intend for you to place this serialised data into permanent storage,
 * with times frames that cross graphql-java versions.  While we don't change things unnecessarily,  we may inadvertently break
 * the serialised compatibility across versions.
 */
@PublicApi
public class NormalizedDocumentEntry implements Serializable {
    private final NormalizedDocument document;

    public NormalizedDocumentEntry(NormalizedDocument document) {
        assertNotNull(document);
        this.document = document;
    }

    public NormalizedDocument getDocument() {
        return document;
    }
}
