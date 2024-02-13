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

    default void executionStrategy_onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {

    }

    default void executionStrategy_onFieldValuesException(Throwable t, ExecutionStrategyParameters parameters) {

    }


    default void executeObject(ExecutionContext executionContext, ExecutionStrategyParameters executionStrategyParameters) {

    }

    default void executeObject_onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {

    }

    default void executeObject_onFieldValuesException(Throwable t, ExecutionStrategyParameters parameters) {

    }

    default void fieldFetched(ExecutionContext executionContext,
                              ExecutionStrategyParameters executionStrategyParameters,
                              DataFetcher<?> dataFetcher,
                              CompletableFuture<Object> fetchedValue) {

    }


    default DataFetcher<?> modifyDataFetcher(DataFetcher<?> dataFetcher) {
        return dataFetcher;
    }
}
