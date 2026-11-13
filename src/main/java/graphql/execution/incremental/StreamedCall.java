package graphql.execution.incremental;

import graphql.Internal;
import graphql.incremental.StreamPayload;

import java.util.concurrent.CompletableFuture;

/**
 * Represents a call that fetches data that was streamed, via the @stream directive.
 * <p>
 * This is a placeholder class, created to showcase the proposed structure that accommodates both @defer and @stream execution.
 */
@Internal
public class StreamedCall implements IncrementalCall<StreamPayload> {
    @Override
    public CompletableFuture<StreamPayload> invoke() {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
