package graphql.execution;

import graphql.Internal;
import graphql.schema.DataFetcher;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Internal
public interface DataLoaderDispatchStrategy {

    DataLoaderDispatchStrategy NO_OP = new DataLoaderDispatchStrategy() {
    };


    default void executionStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {

    }

    default void executionStrategyOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {

    }

    default void executionStrategyOnFieldValuesException(Throwable t, ExecutionStrategyParameters parameters) {

    }


    default void executeObject(ExecutionContext executionContext, ExecutionStrategyParameters executionStrategyParameters) {

    }

    default void executeObjectOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {

    }

    default void executeObjectOnFieldValuesException(Throwable t, ExecutionStrategyParameters parameters) {

    }

    default void fieldFetched(ExecutionContext executionContext,
                              ExecutionStrategyParameters executionStrategyParameters,
                              DataFetcher<?> dataFetcher,
                              CompletableFuture<Object> fetchedValue) {

    }


    default DataFetcher<?> modifyDataFetcher(DataFetcher<?> dataFetcher) {
        return dataFetcher;
    }

    default void deferredField(ExecutionContext executionContext, MergedField currentField) {

    }
}
