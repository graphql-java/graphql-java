package graphql.execution.incremental;

import graphql.incremental.IncrementalPayload;

import java.util.concurrent.CompletableFuture;

/**
 * Represents an incremental call (resulted from the usage of @defer or @stream).
 *
 * @param <T> the type of the payload that this call resolves.
 */
public interface IncrementalCall<T extends IncrementalPayload> {
    CompletableFuture<T> invoke();
}
