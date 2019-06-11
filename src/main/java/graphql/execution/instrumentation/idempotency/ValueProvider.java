package graphql.execution.instrumentation.idempotency;

/**
 * <p>
 * ValueProvider is an extension interface mapping mutation result objects to values stored in the
 * {@link IdempotencyStore}.
 * </p>
 *
 * <p>
 * The default {@link FetchedValueProvider} simply unpacks the fetched value object as-is.
 * Custom implementations may choose to map to a bean property, field value, or whatever is
 * appropriate for the specific use case. See {@link IdValueProvider} for another example.
 * </p>
 *
 * <p>
 * Implementations must be thread-safe. They may return null values, which leads to {@link
 * IdempotencyStore} not storing the mutation result, deactivating idempotency for this specific
 * mutation execution.
 * </p>
 *
 * @author <a href="mailto:mario@ellebrecht.com">Mario Ellebrecht &lt;mario@ellebrecht.com&gt;</a>
 */
@FunctionalInterface
public interface ValueProvider {

  Object getValue(Object value);

}
