package graphql.execution.instrumentation.dataloader;

import graphql.ExperimentalApi;
import graphql.GraphQLContext;
import graphql.execution.ExecutionId;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.ScheduledExecutorService;

/**
 * See {@link DataLoaderDispatchingContextKeys} for how to set it.
 */
@ExperimentalApi
@NullMarked
@FunctionalInterface
public interface DelayedDataLoaderDispatcherExecutorFactory {

    /**
     * Called once per execution to create the {@link ScheduledExecutorService} for the delayed DataLoader dispatching.
     *
     * Will only called if needed, i.e. if there are delayed DataLoaders.
     *
     * @param executionId
     * @param graphQLContext
     *
     * @return
     */
    ScheduledExecutorService createExecutor(ExecutionId executionId, GraphQLContext graphQLContext);
}
