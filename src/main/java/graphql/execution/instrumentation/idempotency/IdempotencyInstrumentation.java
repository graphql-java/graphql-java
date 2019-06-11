package graphql.execution.instrumentation.idempotency;

import graphql.ExecutionResult;
import graphql.execution.AbortExecutionException;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
import graphql.language.OperationDefinition.Operation;
import java.util.Arrays;

/**
 * <p>
 * IdempotencyInstrumentation is an {@link graphql.execution.instrumentation.Instrumentation}
 * implementing an idempotency key mechanism for GraphQL mutations (cf.
 * https://www.enterpriseintegrationpatterns.com/patterns/conversation/RequestResponseRetry.html).
 * This is useful in situations where the client may be unable to process the GraphQL server's
 * response in a timely fashion, e.g. due to network instability, connectivity or power loss. When
 * the client subsequently retries the mutation, IdempotencyInstrumentation will recognize that the
 * mutation was already processed based on the contained idempotency key, and abort execution with a
 * helpful message.
 * </p>
 *
 * <p>
 * Any desired input field can be used as the idempotency key - the default assumes Relay-compliant
 * mutations and reuses the <code>clientMutationId</code> field for this purpose. Adding this
 * instrumentation to a GraphQL instance guarantees that mutations with identical
 * <code>clientMutationId</code> are processed at most once.
 * </p>
 *
 * <p>
 * If a client executes a mutation with a previously used idempotency key, it results in an {@link
 * IdempotencyException} containing both the idempotency key and a value from the previous mutation
 * result.
 * </p>
 *
 * <p>
 * The constructors take implementations for four interface types, described in further detail
 * below, that form a service provider interface enabling extension of this instrumentation to fit
 * any use case required by your domain. For any constructor arguments, <tt>null</tt> may be passed
 * in, meaning to use the default.
 * </p>
 *
 * <p>
 * Keys are stored along with the corresponding previous mutation result value in an {@link
 * IdempotencyStore} which may optionally implement policy-based eviction of keys. The default
 * {@link MemoryIdempotencyStore} uses an unbounded Map on the heap.
 * </p>
 *
 * <p>
 * State is maintained in a scoped manner to avoid delivering previous mutation results to a
 * different scope (e.g. user) than the one that initiated it. {@link ScopeProvider} is used to
 * extract the desired scope from the {@link graphql.execution.ExecutionContext}. The default {@link
 * InputContextScopeProvider} uses {@link graphql.execution.ExecutionContext#getContext()} for this
 * purpose.
 * </p>
 *
 * <p>
 * Idempotency keys for mutations are extracted from the {@link graphql.execution.ExecutionContext}
 * using {@link KeyProvider}, first at the beginning of the execution for checking if the key was
 * previously encountered, and a second time after field completion for storing the result value.
 * The default {@link RelayKeyProvider} assumes Relay-compliant mutations and reuses the
 * <code>clientMutationId</code> field for this purpose
 * (cf. https://facebook.github.io/relay/graphql/mutations.htm).
 * </p>
 *
 * <p>
 * Previous mutation results are stored either as-is or using a {@link ValueProvider} that extracts
 * the value to be stored from the result. The default uses the result object as-is.
 * </p>
 *
 * <p>
 * This instrumentation is thread-safe and stateful, with all runtime state encapsulated in {@link
 * IdempotencyStore}. This means you can safely re-use instances of this class across GraphQL
 * instances that should share the same idempotency key scope.
 * </p>
 *
 * @author <a href="mailto:mario@ellebrecht.com">Mario Ellebrecht &lt;mario@ellebrecht.com&gt;</a>
 */
public final class IdempotencyInstrumentation extends SimpleInstrumentation {

  private static final InstrumentationContext<ExecutionResult> EMPTY_CONTEXT = new SimpleInstrumentationContext<>();

  private final IdempotencyStore store;
  private final ScopeProvider scopeProvider;
  private final KeyProvider keyProvider;
  private final ValueProvider valueProvider;

  public IdempotencyInstrumentation() {
    this(null, null, null, null);
  }

  public IdempotencyInstrumentation(IdempotencyStore store) {
    this(store, null, null, null);
  }

  public IdempotencyInstrumentation(IdempotencyStore store, ScopeProvider scopeProvider) {
    this(store, scopeProvider, null, null);
  }

  public IdempotencyInstrumentation(IdempotencyStore store, ScopeProvider scopeProvider,
      KeyProvider keyProvider) {
    this(store, scopeProvider, keyProvider, null);
  }

  public IdempotencyInstrumentation(IdempotencyStore store, ScopeProvider scopeProvider,
      KeyProvider keyProvider, ValueProvider valueProvider) {
    this.store = store == null ? new MemoryIdempotencyStore() : store;
    this.scopeProvider = scopeProvider == null ? new InputContextScopeProvider() : scopeProvider;
    this.keyProvider = keyProvider == null ? new RelayKeyProvider() : keyProvider;
    this.valueProvider = valueProvider == null ? new FetchedValueProvider() : valueProvider;
  }

  @Override
  public InstrumentationContext<ExecutionResult> beginExecuteOperation(
      InstrumentationExecuteOperationParameters parameters) {
    final ExecutionContext context = parameters.getExecutionContext();
    if (context.getOperationDefinition().getOperation() == Operation.MUTATION) {
      final String key = keyProvider.getKeyFromOperation(context);
      if (key != null && !key.isEmpty()) {
        final Object value = store.get(scopeProvider.getScope(context), key);
        if (value != null) {
          throw new AbortExecutionException(Arrays.asList(new IdempotencyException(key, value)));
        }
      }
    }
    return EMPTY_CONTEXT;
  }

  @Override
  public InstrumentationContext<ExecutionResult> beginFieldComplete(
      InstrumentationFieldCompleteParameters parameters) {
    final ExecutionContext context = parameters.getExecutionContext();
    if (context.getOperationDefinition().getOperation() == Operation.MUTATION) {
      final String key = keyProvider.getKeyFromField(
          context, parameters.getField(), parameters.getTypeInfo());
      if (key != null && !key.isEmpty()) {
        final Object value = valueProvider.getValue(parameters.getFetchedValue());
        if (value != null) {
          store.put(scopeProvider.getScope(context), key, value);
        }
      }
    }
    return EMPTY_CONTEXT;
  }

}
