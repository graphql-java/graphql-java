package graphql.execution;

import graphql.ExperimentalApi;
import graphql.GraphQLContext;
import org.jspecify.annotations.NullMarked;

@ExperimentalApi
@NullMarked
public interface EngineRunningObserver {


    String ENGINE_RUNNING_OBSERVER_KEY = "__ENGINE_RUNNING_OBSERVER";

    void runningStateChanged(ExecutionId executionId, GraphQLContext graphQLContext, boolean runningState);
}
