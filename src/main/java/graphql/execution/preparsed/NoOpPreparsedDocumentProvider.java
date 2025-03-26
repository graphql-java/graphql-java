package graphql.execution.preparsed;


import graphql.ExecutionInput;
import graphql.Internal;
import graphql.execution.CF;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Internal
public class NoOpPreparsedDocumentProvider implements PreparsedDocumentProvider {
    public static final NoOpPreparsedDocumentProvider INSTANCE = new NoOpPreparsedDocumentProvider();

    @Override
    public CompletableFuture<PreparsedDocumentEntry> getDocumentAsync(ExecutionInput executionInput, Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
        return CF.completedEngineCF(parseAndValidateFunction.apply(executionInput));
    }
}
