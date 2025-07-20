package graphql.normalized.nf.provider

import graphql.ExecutionInput
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.execution.preparsed.PreparsedDocumentProvider

import java.util.concurrent.CompletableFuture
import java.util.function.Function


class TestingNormalizedDocumentProvider implements NormalizedDocumentProvider {
    Map<String, NormalizedDocumentEntry> cache = new HashMap<>()

    @Override
    CompletableFuture<NormalizedDocumentEntry> getNormalizedDocument(ExecutionInput executionInput, CreateNormalizedDocument creator) {
        Function<String, NormalizedDocumentEntry> mapCompute = { key -> new NormalizedDocumentEntry(creator.createNormalizedDocument()) }
        return CompletableFuture.completedFuture(cache.computeIfAbsent(executionInput.query, mapCompute))
    }
}
