package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLObjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static graphql.execution.Execution.collectFields;
import static graphql.execution.Execution.getExecutionStrategyParameters;
import static graphql.execution.Execution.getOperationRootType;
import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * The standard graphql execution strategy that runs fields asynchronously non-blocking, honoring @defer directives.
 *
 * This implementation takes a simple-minded in that everything not marked with @defer will be processed in a first pass,
 * and that result returned.  Then, in a second pass, everything marked with @defer will be processed, and returned.
 * A more sophisticated, nuanced implementation could break that second payload up into multiple payloads.
 *
 * The implementation also leverage that of SubscriptionExecutionStrategy.
 */
public class DeferringAsyncExecutionStrategy extends AsyncExecutionStrategy {
    private static final Logger log = LoggerFactory.getLogger(DeferringAsyncExecutionStrategy.class);

    private final FieldCollector nonDeferredFieldCollector = NonDeferredFieldCollector.nonDeferredFieldCollector();
    private final FieldCollector deferredFieldCollector = new DeferredFieldCollector();

    /**
     * The standard graphql execution strategy that runs fields asynchronously non-blocking, honoring @defer directives
     */
    public DeferringAsyncExecutionStrategy() {
        super();
    }

    public DeferringAsyncExecutionStrategy(DataFetcherExceptionHandler dataFetcherExceptionHandler) {
        super(dataFetcherExceptionHandler);
    }

    /*
    private static String lastDeferredResults;

    public static String getlastDeferredResults() {
        return lastDeferredResults;
    }
    */

    @Override
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
        return executeDeferringAsyncExecutionStrategy(executionContext, parameters);
    }

    private CompletableFuture<ExecutionResult> executeDeferringAsyncExecutionStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationExecutionStrategyParameters instrumentationParameters = new InstrumentationExecutionStrategyParameters(executionContext, parameters);

        InstrumentationContext<ExecutionResult> executionStrategyCtx = instrumentation.beginExecutionStrategy(instrumentationParameters);

        OperationDefinition operationDefinition = executionContext.getOperationDefinition();

        GraphQLObjectType operationRootType;
        try {
            operationRootType = getOperationRootType(executionContext.getGraphQLSchema(), operationDefinition);
        } catch (RuntimeException rte) {
            if (rte instanceof GraphQLError) {
                ExecutionResult executionResult = new ExecutionResultImpl(Collections.singletonList((GraphQLError) rte));
                CompletableFuture<ExecutionResult> resultCompletableFuture = completedFuture(executionResult);

                executionStrategyCtx.onDispatched(resultCompletableFuture);
                executionStrategyCtx.onCompleted(executionResult, rte);
                return resultCompletableFuture;
            }
            throw rte;
        }

        Map<String, List<Field>> deferredFields = collectDeferredFields(executionContext, operationRootType, operationDefinition);
        if (deferredFields.isEmpty()) {
            return super.execute(executionContext, parameters, true);
        } else {
            CompletableFuture<ExecutionResult> nonDeferredResult =
                    executeNonDeferredFields(executionContext, operationRootType, operationDefinition);

            ExecutionStrategyParameters deferredParameters = getExecutionStrategyParameters(executionContext,
                    executionContext.getRoot(), operationRootType, deferredFields);
            CompletableFuture<ExecutionResult> deferredResult = super.execute(executionContext, deferredParameters);
            try {
//                lastDeferredResults = deferredResult.get().toString();
                log.debug("Deferred execution result: {}",  deferredResult.get().toString());
            } catch (InterruptedException | ExecutionException e) { }

            return nonDeferredResult;
        }
    }

    private Map<String, List<Field>>  collectDeferredFields(ExecutionContext executionContext, GraphQLObjectType operationRootType, OperationDefinition operationDefinition) {
        log.debug("Deferred collectFields:");
        Map<String, List<Field>> deferredFields = collectFields(executionContext, operationRootType, operationDefinition,
                deferredFieldCollector);
        if (log.isDebugEnabled()) {
            log.debug("deferred: {}", deferredFields.size());
            for (String key : deferredFields.keySet()) log.debug(" {}", key);
        }
        return deferredFields;
    }

    private CompletableFuture<ExecutionResult> executeNonDeferredFields(ExecutionContext executionContext, GraphQLObjectType operationRootType, OperationDefinition operationDefinition) {
        Map<String, List<Field>> nonDeferredFields = collectNonDeferredFields(executionContext, operationRootType, operationDefinition);
        ExecutionStrategyParameters nonDeferredParameters = getExecutionStrategyParameters(executionContext,
                executionContext.getRoot(), operationRootType, nonDeferredFields);

        log.debug("Non-Deferred execution");
        return super.execute(executionContext, nonDeferredParameters);
    }

    private Map<String, List<Field>> collectNonDeferredFields(ExecutionContext executionContext, GraphQLObjectType operationRootType, OperationDefinition operationDefinition) {
        log.debug("Non-Deferred collectFields:");
        Map<String, List<Field>> nonDeferredFields = collectFields(executionContext, operationRootType, operationDefinition,
                nonDeferredFieldCollector);
        if (log.isDebugEnabled()) {
            log.debug("nondeferred: {}", nonDeferredFields.size());
            for (String key : nonDeferredFields.keySet()) log.debug(" {}", key);
        }
        return nonDeferredFields;
    }

    /*
        class ExecutionPublisher<T> implements Publisher<T> {
            CompletableFuture<ExecutionResult> nonDeferredResult;
            CompletableFuture<ExecutionResult> deferredResult;

            public ExecutionPublisher(CompletableFuture<ExecutionResult> nonDeferredResult,
                                      CompletableFuture<ExecutionResult> deferredResult) {
                this.nonDeferredResult = nonDeferredResult;
                this.deferredResult = deferredResult;
           }

            @Override
            public void subscribe(Subscriber<? super T> subscriber) {
                try {
                    subscriber.onNext(nonDeferredResult);
                    subscriber.onNext();
                    subscriber.onComplete();
                } catch (Throwable e) {
                    subscriber.onError(e);
                }
            }
        }
        */
}
