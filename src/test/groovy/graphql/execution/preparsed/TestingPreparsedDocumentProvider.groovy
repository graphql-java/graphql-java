package graphql.execution.preparsed

import graphql.ExecutionInput

import java.util.function.Function


class TestingPreparsedDocumentProvider implements PreparsedDocumentProvider {
    private Map<String, PreparsedDocumentEntry> cache = new HashMap<>()

    @Override
    PreparsedDocumentEntry getDocument(ExecutionInput executionInput, Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
        Function<String, PreparsedDocumentEntry> mapCompute = { key -> parseAndValidateFunction.apply(executionInput) }
        return cache.computeIfAbsent(executionInput.query, mapCompute)
    }

}
