package graphql.execution.instrumentation.idempotency;

/**
 * <p>
 * IdempotencyStore defines the service provider interface (SPI) for classes storing scoped (e.g.
 * user-dependent) idempotency keys and the value a previous mutation resulted in.
 * </p>
 *
 * <p>
 * The default {@link MemoryIdempotencyStore} simply uses a Map on the heap.
 * Custom implementations may use a filesystem, database, key-value store, distributed data
 * structures of a cluster manager, or whatever is appropriate for the specific use case.
 * Implementations may also choose to evict entries based on expiration (e.g. after 31 days) to
 * support domain logic and to avoid storage overflow.
 * </p>
 *
 * <p>
 * Implementations must be thread-safe to work properly in the context of multi-threaded access to
 * the instrumentation making use of the store.
 * </p>
 *
 * @author <a href="mailto:mario@ellebrecht.com">Mario Ellebrecht &lt;mario@ellebrecht.com&gt;</a>
 */
public interface IdempotencyStore {

  Object get(Object scope, String key);

  Object put(Object scope, String key, Object value);

}
