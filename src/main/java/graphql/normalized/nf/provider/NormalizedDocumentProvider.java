package graphql.normalized.nf.provider;

import graphql.ExecutionInput;
import graphql.PublicSpi;

import java.util.concurrent.CompletableFuture;

/**
 * Interface that allows clients to hook in normalized document caching.
 */
@PublicSpi
public interface NormalizedDocumentProvider {
    CompletableFuture<NormalizedDocumentEntry> getNormalizedDocument(ExecutionInput executionInput, CreateNormalizedDocument creator);
}



