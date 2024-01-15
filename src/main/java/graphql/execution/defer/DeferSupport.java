package graphql.execution.defer;

import graphql.DeferredExecutionResult;
import graphql.Internal;
import graphql.execution.ExecutionContext;
import graphql.execution.MergedField;
import graphql.execution.ValuesResolver;
import graphql.execution.reactive.SingleSubscriberPublisher;
import graphql.language.Directive;
import graphql.language.Field;
import org.reactivestreams.Publisher;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

import static graphql.Directives.DeferDirective;

/**
 * This provides support for @defer directives on fields that mean that results will be sent AFTER
 * the main result is sent via a Publisher stream.
 */
@Internal
// TODO: This should be called IncrementalSupport and handle both @defer and @stream
public class DeferSupport {

    private final AtomicBoolean deferDetected = new AtomicBoolean(false);
    private final Deque<DeferredCall> deferredCalls = new ConcurrentLinkedDeque<>();
    private final SingleSubscriberPublisher<DeferredExecutionResult> publisher = new SingleSubscriberPublisher<>();

    public boolean checkForDeferDirective(MergedField currentField, ExecutionContext executionContext) {
        for (Field field : currentField.getFields()) {
            List<Directive> directives = field.getDirectives(DeferDirective.getName());
            // TODO: How to best deal with repeated directives here - @defer/@stream is not a repeated directive
            Directive directive = directives.stream().findFirst().orElse(null);
            if (directive != null) {
                Map<String, Object> argumentValues = ValuesResolver.getArgumentValues(
                        DeferDirective.getArguments(),
                        directive.getArguments(),
                        executionContext.getCoercedVariables(),
                        executionContext.getGraphQLContext(),
                        executionContext.getLocale()
                );
                return (Boolean) argumentValues.get("if");
            }
        }
        return false;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private void drainDeferredCalls() {
        if (deferredCalls.isEmpty()) {
            publisher.noMoreData();
            return;
        }
        DeferredCall deferredCall = deferredCalls.pop();
        CompletableFuture<DeferredExecutionResult> future = deferredCall.invoke();
        future.whenComplete((executionResult, exception) -> {
            if (exception != null) {
                publisher.offerError(exception);
                return;
            }
            publisher.offer(executionResult);
            drainDeferredCalls();
        });
    }

    public void enqueue(DeferredCall deferredCall) {
        deferDetected.set(true);
        deferredCalls.offer(deferredCall);
    }

    public boolean isDeferDetected() {
        return deferDetected.get();
    }

    /**
     * When this is called the deferred execution will begin
     *
     * @return the publisher of deferred results
     */
    public Publisher<DeferredExecutionResult> startDeferredCalls() {
        drainDeferredCalls();
        return publisher;
    }
}
