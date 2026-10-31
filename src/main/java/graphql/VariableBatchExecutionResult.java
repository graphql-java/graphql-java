package graphql;

import org.jspecify.annotations.NullMarked;
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The result of supplying more than one variable map to {@link GraphQL#executeAsync(ExecutionInput)}.
 * Execution starts when the results publisher is subscribed to and receives demand. Each entry contains an ordinary
 * execution result, including its own errors and, when applicable, its incremental publisher.
 * {@link #getData()} returns the same publisher as {@link #getResults()}.
 * <p>
 * Choose either streaming via {@link #getResults()} or collection via {@link #getResultsAsync()}:
 * the publisher supports one subscriber. There is no single GraphQL response map for a batch, so
 * {@link #toSpecification()} is unsupported. Serialize the individual execution results instead.
 * <pre>{@code
 * ExecutionInput input = ExecutionInput.newExecutionInput()
 *     .query("query($id: ID!) { product(id: $id) { name } }")
 *     .variables(List.of(Map.of("id", "a"), Map.of("id", "b")))
 *     .dataLoaderRegistry(registry)
 *     .build();
 * VariableBatchExecutionResult batch = (VariableBatchExecutionResult) graphQL.execute(input);
 * CompletableFuture<List<ExecutionResult>> results = batch.getResultsAsync();
 * }</pre>
 * The entries share DataLoaders and caches for queries and mutations; batch dispatch is always automatic.
 * Mutations retain serial root fields within each entry; there is no ordering guarantee between
 * entries. Applications must clear or prime affected loader cache entries after writes.
 * Each entry has separate variable coercion, errors, instrumentation state, cancellation state,
 * execution id and a shallow copy of the GraphQL context. Application objects in that context,
 * the root and the local context remain shared.
 * <p>
 * The configured concurrency limit bounds executions up to their initial result and buffered
 * initial results. Incremental publishers retain their existing lifecycle: the consumer must
 * subscribe to each one to receive subsequent payloads and control their consumption separately.
 * Cancelling the input also cancels entries whose initial results have already been delivered.
 * Subscription operations are not supported in a variable batch.
 */
@ExperimentalApi
@NullMarked
public interface VariableBatchExecutionResult extends ExecutionResult {
    /** @return indexed results in completion order, subject to subscriber demand */
    Publisher<VariableBatchResult> getResults();

    /** @return the number of executions in the batch */
    int getSize();

    /**
     * Subscribes to the results and collects them in input order. Cancelling the returned future
     * cancels the batch subscription. Incremental results retain their individual publishers.
     *
     * @return a future containing the individual results in input order
     */
    CompletableFuture<List<ExecutionResult>> getResultsAsync();
}
