package graphql.execution.preparsed;

import graphql.ExecutionInput;
import graphql.Internal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@Internal
@NullMarked
public final class VariableBatchPreparsedDocumentProvider implements PreparsedDocumentProvider {
    private final AtomicReference<@Nullable CompletableFuture<PreparsedDocumentEntry>> prepared = new AtomicReference<>();

    @Override
    public CompletableFuture<PreparsedDocumentEntry> getDocumentAsync(ExecutionInput input,
                                                                      Function<ExecutionInput, PreparsedDocumentEntry> compute) {
        CompletableFuture<PreparsedDocumentEntry> result = new CompletableFuture<>();
        if (!prepared.compareAndSet(null, result)) {
            return graphql.Assert.assertNotNull(prepared.get());
        }
        try {
            result.complete(compute.apply(input));
        } catch (Throwable failure) {
            result.completeExceptionally(failure);
        }
        return result;
    }
}
