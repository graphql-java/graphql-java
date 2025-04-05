package graphql.execution;

import graphql.ExecutionInput;
import graphql.ExperimentalApi;
import graphql.GraphQLContext;
import org.jspecify.annotations.NullMarked;

/**
 * This class lets you observe the running state of the graphql-java engine.  As it processes and dispatches graphql fields,
 * the engine moves in and out of a running and not running state.  As it does this, the callback is called with information telling you the current
 * state.
 * <p>
 * If the engine is cancelled via {@link ExecutionInput#cancel()} then the observer will also be called to indicate that.
 */
@ExperimentalApi
@NullMarked
public interface EngineRunningObserver {

    enum RunningState {
        /**
         * Represents that the engine code is actively running its own code
         */
        RUNNING,
        /**
         * Represents that the engine code is asynchronously waiting for fetching to happen
         */
        NOT_RUNNING,
        /**
         * Represents that the engine code has been cancelled via {@link ExecutionInput#cancel()}
         */
        CANCELLED
    }


    String ENGINE_RUNNING_OBSERVER_KEY = "__ENGINE_RUNNING_OBSERVER";


    /**
     * This will be called when the running state of the graphql-java engine changes.
     *
     * @param executionId    the id of the current execution
     * @param graphQLContext the graphql context
     */
    void runningStateChanged(ExecutionId executionId, GraphQLContext graphQLContext, RunningState runningState);
}
