package graphql;

import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The result of supplying more than one variable map to {@link GraphQL#executeAsync(ExecutionInput)}.
 * Each entry is submitted to {@code executeAsync} in input order, without waiting for earlier futures
 * to complete. {@link #getResults()} exposes those futures; {@link #getResultsAsync()} collects their
 * results in input order. Batching does not create threads or limit the number of executions in flight.
 * {@link #getData()} returns the same list of futures as {@link #getResults()}.
 * There is no single GraphQL response map for a batch, so {@link #toSpecification()} is unsupported.
 * Serialize the individual execution results instead.
 * <pre>{@code
 * ExecutionInput input = ExecutionInput.newExecutionInput()
 *     .query("query($id: ID!) { product(id: $id) { name } }")
 *     .variables(List.of(Map.of("id", "a"), Map.of("id", "b")))
 *     .dataLoaderRegistry(registry)
 *     .build();
 * VariableBatchExecutionResult batch = (VariableBatchExecutionResult) graphQL.execute(input);
 * CompletableFuture<List<ExecutionResult>> results = batch.getResultsAsync();
 * }</pre>
 * The entries share DataLoaders and caches for queries and mutations, using ordinary dispatch behavior.
 * Mutations retain serial root fields within each entry; there is no ordering guarantee between
 * entries. Applications must clear or prime affected loader cache entries after writes.
 * Each entry has separate variable coercion, errors, instrumentation state, cancellation state,
 * execution id and a shallow copy of the GraphQL context. Application objects in that context,
 * the root and the local context remain shared. Cancelling the input requests cancellation of each entry.
 * Incremental results retain their individual publishers and lifecycle.
 * Subscription operations are rejected with {@link IllegalArgumentException} as soon as the selected operation is known.
 */
@ExperimentalApi
@NullMarked
public interface VariableBatchExecutionResult extends ExecutionResult {
    /** @return an immutable list of execution futures in input order */
    List<CompletableFuture<ExecutionResult>> getResults();

    /** @return the number of executions in the batch */
    int getSize();

    /**
     * Collects the individual results in input order. This does not start new executions.
     * Cancelling the returned future does not cancel the executions; use {@link ExecutionInput#cancel()} instead.
     *
     * @return a future containing an immutable list of the individual results in input order
     */
    CompletableFuture<List<ExecutionResult>> getResultsAsync();
}
