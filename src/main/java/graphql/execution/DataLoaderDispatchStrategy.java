package graphql.execution;

import graphql.Internal;
import graphql.execution.incremental.AlternativeCallContext;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.util.List;
import java.util.function.Supplier;

@Internal
public interface DataLoaderDispatchStrategy {

    DataLoaderDispatchStrategy NO_OP = new DataLoaderDispatchStrategy() {
    };


    default void executionStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters, int fieldCount) {

    }

    default void executionSerialStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {

    }

    default void executionStrategyOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {

    }

    default void executionStrategyOnFieldValuesException(Throwable t, ExecutionStrategyParameters parameters) {

    }


    default void executeObject(ExecutionContext executionContext, ExecutionStrategyParameters executionStrategyParameters, int fieldCount) {

    }

    default void executeObjectOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {

    }

    default void deferredOnFieldValue(String resultKey, FieldValueInfo fieldValueInfo, Throwable throwable, ExecutionStrategyParameters parameters) {

    }

    default void executeObjectOnFieldValuesException(Throwable t, ExecutionStrategyParameters parameters) {

    }

    default void fieldFetched(ExecutionContext executionContext,
                              ExecutionStrategyParameters executionStrategyParameters,
                              DataFetcher<?> dataFetcher,
                              Object fetchedValue,
                              Supplier<DataFetchingEnvironment> dataFetchingEnvironment) {

    }


    default void newSubscriptionExecution(AlternativeCallContext alternativeCallContext) {

    }

    default void subscriptionEventCompletionDone(AlternativeCallContext alternativeCallContext) {

    }

    default void finishedFetching(ExecutionContext executionContext, ExecutionStrategyParameters newParameters) {

    }

    default void deferFieldFetched(ExecutionStrategyParameters executionStrategyParameters) {

    }

    default void startComplete(ExecutionStrategyParameters parameters) {

    }

    default void stopComplete(ExecutionStrategyParameters parameters) {

    }
}
