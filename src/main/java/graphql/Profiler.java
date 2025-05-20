package graphql;

import graphql.execution.EngineRunningObserver;
import graphql.execution.ExecutionId;
import graphql.execution.ResultPath;
import graphql.schema.DataFetcher;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Internal
@NullMarked
public interface Profiler {


    Profiler NO_OP = new Profiler() {
    };

    default void start() {

    }


    default void rootFieldCount(int size) {

    }

    default void subSelectionCount(int size) {

    }

    default void setExecutionId(ExecutionId executionId) {

    }

    default void fieldFetched(Object fetchedObject, DataFetcher<?> dataFetcher, ResultPath path) {

    }

    default @Nullable EngineRunningObserver wrapEngineRunningObserver(EngineRunningObserver engineRunningObserver) {
        return engineRunningObserver;
    }
}
