package graphql.engine.original;

import graphql.ExecutionResult;
import graphql.engine.EngineParameters;
import graphql.engine.GraphQLEngine;
import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.AsyncSerialExecutionStrategy;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategy;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.NonNullableFieldWasNullException;
import graphql.execution.SimpleDataFetcherExceptionHandler;
import graphql.execution.SubscriptionExecutionStrategy;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import graphql.execution.instrumentation.original.ChainedOriginalInstrumentation;
import graphql.execution.instrumentation.original.NoContextChainedOriginalInstrumentation;
import graphql.execution.instrumentation.original.SimplePerformantOriginalInstrumentation;
import graphql.language.OperationDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;

public class OriginalGraphQlEngine implements GraphQLEngine {

    private final DataFetcherExceptionHandler defaultExceptionHandler;

    private final ExecutionStrategy queryExecutionStrategy;

    private final ExecutionStrategy mutationExecutionStrategy;
    private final ExecutionStrategy subscriptionExecutionStrategy;
    private final Instrumentation instrumentation;

    private OriginalGraphQlEngine(Builder builder) {
        this.defaultExceptionHandler = builder.defaultExceptionHandler;
        this.queryExecutionStrategy = builder.queryExecutionStrategy;
        this.mutationExecutionStrategy = builder.mutationExecutionStrategy;
        this.subscriptionExecutionStrategy = builder.subscriptionExecutionStrategy;
        this.instrumentation = builder.instrumentation;
    }

    @Override
    public Instrumentation getInstrumentation() {
        return instrumentation;
    }

    public ExecutionStrategy getStrategy(OperationDefinition.Operation operation) {
        if (operation == OperationDefinition.Operation.MUTATION) {
            return getMutationStrategy();
        } else if (operation == OperationDefinition.Operation.SUBSCRIPTION) {
            return getSubscriptionStrategy();
        } else {
            return getQueryStrategy();
        }
    }

    @Override
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, EngineParameters parameters) throws NonNullableFieldWasNullException {
        ExecutionStrategyParameters strategyParameters = ExecutionStrategyParameters.newParameters()
                .executionStepInfo(parameters.getExecutionStepInfo())
                .source(parameters.getSource())
                .localContext(parameters.getLocalContext())
                .fields(parameters.getFields())
                .nonNullFieldValidator(parameters.getNonNullableFieldValidator())
                .path(parameters.getPath())
                .build();
        ExecutionStrategy executionStrategy = getStrategy(parameters.getOperation());
        return executionStrategy.execute(executionContext, strategyParameters);
    }

    public DataFetcherExceptionHandler getDefaultExceptionHandler() {
        return defaultExceptionHandler;
    }

    public ExecutionStrategy getQueryStrategy() {
        return queryExecutionStrategy;
    }

    public ExecutionStrategy getMutationStrategy() {
        return mutationExecutionStrategy;
    }

    public ExecutionStrategy getSubscriptionStrategy() {
        return subscriptionExecutionStrategy;
    }

    public OriginalGraphQlEngine transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static Builder newEngine() {
        return new Builder();
    }

    public static class Builder {

        private DataFetcherExceptionHandler defaultExceptionHandler = new SimpleDataFetcherExceptionHandler();

        private ExecutionStrategy queryExecutionStrategy;

        private ExecutionStrategy mutationExecutionStrategy;
        private ExecutionStrategy subscriptionExecutionStrategy;
        private Instrumentation instrumentation = null; // deliberate default here
        private boolean doNotAddDefaultInstrumentations = false;

        public Builder() {
        }

        private Builder(OriginalGraphQlEngine other) {
            this.queryExecutionStrategy = other.queryExecutionStrategy;
            this.mutationExecutionStrategy = other.mutationExecutionStrategy;
            this.subscriptionExecutionStrategy = other.subscriptionExecutionStrategy;
            this.instrumentation = other.instrumentation;
        }

        public Builder queryExecutionStrategy(ExecutionStrategy executionStrategy) {
            this.queryExecutionStrategy = assertNotNull(executionStrategy, () -> "Query ExecutionStrategy must be non null");
            return this;
        }

        public Builder mutationExecutionStrategy(ExecutionStrategy executionStrategy) {
            this.mutationExecutionStrategy = assertNotNull(executionStrategy, () -> "Mutation ExecutionStrategy must be non null");
            return this;
        }

        public Builder subscriptionExecutionStrategy(ExecutionStrategy executionStrategy) {
            this.subscriptionExecutionStrategy = assertNotNull(executionStrategy, () -> "Subscription ExecutionStrategy must be non null");
            return this;
        }

        /**
         * This allows you to set a default {@link graphql.execution.DataFetcherExceptionHandler} that will be used to handle exceptions that happen
         * in {@link graphql.schema.DataFetcher} invocations.
         *
         * @param dataFetcherExceptionHandler the default handler for data fetching exception
         *
         * @return this builder
         */
        public Builder defaultDataFetcherExceptionHandler(DataFetcherExceptionHandler dataFetcherExceptionHandler) {
            this.defaultExceptionHandler = assertNotNull(dataFetcherExceptionHandler, () -> "The DataFetcherExceptionHandler must be non null");
            return this;
        }

        public Builder instrumentation(Instrumentation instrumentation) {
            this.instrumentation = instrumentation;
            return this;
        }

        /**
         * For performance reasons you can opt into situation where the default instrumentations (such
         * as {@link graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation} will not be
         * automatically added into the graphql instance.
         * <p>
         * For most situations this is not needed unless you are really pushing the boundaries of performance
         * <p>
         * By default, a certain graphql instrumentations will be added to the mix to more easily enable certain functionality.  This
         * allows you to stop this behavior
         *
         * @return this builder
         */
        public Builder doNotAddDefaultInstrumentations() {
            this.doNotAddDefaultInstrumentations = true;
            return this;
        }

        private static Instrumentation checkInstrumentationDefaultState(Instrumentation instrumentation, boolean doNotAddDefaultInstrumentations) {
            if (doNotAddDefaultInstrumentations) {
                return instrumentation == null ? SimplePerformantOriginalInstrumentation.INSTANCE : instrumentation;
            }
            if (instrumentation instanceof DataLoaderDispatcherInstrumentation) {
                return instrumentation;
            }
            if (instrumentation instanceof NoContextChainedOriginalInstrumentation) {
                return instrumentation;
            }
            if (instrumentation == null) {
                return new DataLoaderDispatcherInstrumentation();
            }

            //
            // if we don't have a DataLoaderDispatcherInstrumentation in play, we add one.  We want DataLoader to be 1st class in graphql without requiring
            // people to remember to wire it in.  Later we may decide to have more default instrumentations but for now it's just the one
            //
            List<Instrumentation> instrumentationList = new ArrayList<>();
            if (instrumentation instanceof ChainedInstrumentation) {
                instrumentationList.addAll(((ChainedInstrumentation) instrumentation).getInstrumentations());
            } else {
                instrumentationList.add(instrumentation);
            }
            boolean containsDLInstrumentation = instrumentationList.stream().anyMatch(instr -> instr instanceof DataLoaderDispatcherInstrumentation);
            if (!containsDLInstrumentation) {
                instrumentationList.add(new DataLoaderDispatcherInstrumentation());
            }
            return new ChainedOriginalInstrumentation(instrumentationList);
        }


        public OriginalGraphQlEngine build() {
            // we use the data fetcher exception handler unless they set their own strategy in which case bets are off
            if (queryExecutionStrategy == null) {
                this.queryExecutionStrategy = new AsyncExecutionStrategy(this.defaultExceptionHandler);
            }
            if (mutationExecutionStrategy == null) {
                this.mutationExecutionStrategy = new AsyncSerialExecutionStrategy(this.defaultExceptionHandler);
            }
            if (subscriptionExecutionStrategy == null) {
                this.subscriptionExecutionStrategy = new SubscriptionExecutionStrategy(this.defaultExceptionHandler);
            }

            this.instrumentation = checkInstrumentationDefaultState(this.instrumentation, this.doNotAddDefaultInstrumentations);
            return new OriginalGraphQlEngine(this);
        }
    }
}
