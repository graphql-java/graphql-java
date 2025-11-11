package graphql.execution.preparsed;


import graphql.ExecutionInput;
import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * A {@link PreparsedDocumentProvider that does nothing}
 */
@PublicApi
@NullMarked
public class NoOpPreparsedDocumentProvider implements PreparsedDocumentProvider {
    public static final NoOpPreparsedDocumentProvider INSTANCE = new NoOpPreparsedDocumentProvider();

    @Override
    public CompletableFuture<PreparsedDocumentEntry> getDocumentAsync(ExecutionInput executionInput, Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
        return CompletableFuture.completedFuture(parseAndValidateFunction.apply(executionInput));
    }
}
