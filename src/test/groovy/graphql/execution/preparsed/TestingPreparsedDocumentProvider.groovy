package graphql.execution.preparsed

import graphql.ExecutionInput

import java.util.concurrent.CompletableFuture
import java.util.function.Function


class TestingPreparsedDocumentProvider implements PreparsedDocumentProvider {
    private Map<String, PreparsedDocumentEntry> cache = new HashMap<>()

    @Override
    CompletableFuture<PreparsedDocumentEntry> getDocumentAsync(ExecutionInput executionInput, Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
        Function<String, PreparsedDocumentEntry> mapCompute = { key -> parseAndValidateFunction.apply(executionInput) }
        return CompletableFuture.completedFuture(cache.computeIfAbsent(executionInput.query, mapCompute))
    }

}
