package graphql.normalized.nf.provider;

import graphql.Internal;

import java.util.concurrent.CompletableFuture;

@Internal
public class NoOpNormalizedDocumentProvider implements NormalizedDocumentProvider {
    public static final NoOpNormalizedDocumentProvider INSTANCE = new NoOpNormalizedDocumentProvider();

    @Override
    public CompletableFuture<NormalizedDocumentEntry> getNormalizedDocument(CreateNormalizedDocument creator) {
        return CompletableFuture.completedFuture(new NormalizedDocumentEntry(creator.createNormalizedDocument()));
    }
}
