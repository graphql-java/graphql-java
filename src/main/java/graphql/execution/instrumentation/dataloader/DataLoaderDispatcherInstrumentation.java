package graphql.execution.instrumentation.dataloader;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.NoOpInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import org.dataloader.DataLoaderRegistry;

import java.util.concurrent.CompletableFuture;

/**
 * This graphql {@link graphql.execution.instrumentation.Instrumentation} will dispatch
 * all the contained {@link org.dataloader.DataLoader}s when each level of the graphql
 * query is executed.
 *
 * This allows you to use {@link org.dataloader.DataLoader}s in your {@link graphql.schema.DataFetcher}s
 * to optimal loading of data.
 *
 * @see org.dataloader.DataLoader
 * @see org.dataloader.DataLoaderRegistry
 */
public class DataLoaderDispatcherInstrumentation extends NoOpInstrumentation {

    private final DataLoaderRegistry dataLoaderRegistry;

    /**
     * You pass in a registry of N data loaders which will be {@link org.dataloader.DataLoader#dispatch() dispatched} as
     * each level of the query executes.
     *
     * @param dataLoaderRegistry the regsitry of data loaders that will be dispatched
     */
    public DataLoaderDispatcherInstrumentation(DataLoaderRegistry dataLoaderRegistry) {
        this.dataLoaderRegistry = dataLoaderRegistry;
    }

    @Override
    public InstrumentationContext<CompletableFuture<ExecutionResult>> beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        return (result, t) -> {
            if (t == null) {
                // only dispatch when there are no errors
                dataLoaderRegistry.dispatchAll();
            }
        };
    }
}