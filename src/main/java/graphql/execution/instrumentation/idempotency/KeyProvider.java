package graphql.execution.instrumentation.idempotency;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.schema.GraphQLFieldDefinition;

/**
 * <p>
 * KeyProvider is an extension interface for extracting the idempotency key value from a mutation.
 * using {@link ExecutionContext}.
 * </p>
 *
 * <p>
 * The default {@link RelayKeyProvider} assumes Relay-compliant mutations and uses the String value
 * of the <code>clientMutationId</code> input field for this purpose.
 * Custom implementations may use specific input fields instead, or other data from the
 * execution context.
 * </p>
 *
 * <p>
 * Implementations must be thread-safe. They may return null keys, which leads to {@link
 * IdempotencyInstrumentation} ignoring the key, deactivating idempotency for this specific mutation
 * execution.
 * </p>
 *
 * @author <a href="mailto:mario@ellebrecht.com">Mario Ellebrecht &lt;mario@ellebrecht.com&gt;</a>
 */
public interface KeyProvider {

  String getKeyFromOperation(ExecutionContext context);

  String getKeyFromField(ExecutionContext context, GraphQLFieldDefinition fieldDefinition,
      ExecutionStepInfo typeInfo);

}
