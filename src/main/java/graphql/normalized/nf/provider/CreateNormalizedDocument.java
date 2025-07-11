package graphql.normalized.nf.provider;

import graphql.normalized.nf.NormalizedDocument;

@FunctionalInterface
public interface CreateNormalizedDocument {
    NormalizedDocument createNormalizedDocument();
}
