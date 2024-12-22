package graphql.execution;

import graphql.Internal;
import graphql.schema.DataFetcher;

import java.util.List;

@Internal
public interface DataLoaderDispatchStrategy {

    DataLoaderDispatchStrategy NO_OP = new DataLoaderDispatchStrategy() {
    };


    default void executionStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {

    }

    default void executionSerialStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {

    }

    default void executionStrategyOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) {

    }

    default void executionStrategyOnFieldValuesException(Throwable t) {

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
                              Object fetchedValue) {

    }


    default DataFetcher<?> modifyDataFetcher(DataFetcher<?> dataFetcher) {
        return dataFetcher;
    }

    default void executeDeferredOnFieldValueInfo(FieldValueInfo fieldValueInfo, ExecutionStrategyParameters executionStrategyParameters) {

    }
}
