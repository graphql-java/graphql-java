package graphql;

import graphql.execution.EngineRunningObserver;
import graphql.execution.ResultPath;
import graphql.language.OperationDefinition;
import graphql.schema.DataFetcher;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Internal
@NullMarked
public interface Profiler {


    Profiler NO_OP = new Profiler() {
    };


    default void rootFieldCount(int size) {

    }

    default void subSelectionCount(int size) {

    }

    default void executionInput(ExecutionInput executionInput) {

    }

    default void fieldFetched(Object fetchedObject, DataFetcher<?> dataFetcher, ResultPath path) {

    }

    default @Nullable EngineRunningObserver wrapEngineRunningObserver(EngineRunningObserver engineRunningObserver) {
        return engineRunningObserver;
    }

    default void operationDefinition(OperationDefinition operationDefinition) {

    }
}
