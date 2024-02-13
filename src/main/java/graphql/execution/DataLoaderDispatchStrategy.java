package graphql.execution;

import graphql.Internal;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Internal
public interface DataLoaderDispatchStrategy {

    public static final DataLoaderDispatchStrategy NO_OP = new DataLoaderDispatchStrategy() {
    };


    default void executionStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {

    }

    default void executionStrategy_onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) {

    }

    default void executionStrategy_onFieldValuesException(Throwable t) {

    }


    default void executeObject(ExecutionContext executionContext, ExecutionStrategyParameters executionStrategyParameters) {

    }

    default void executeObject_onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) {

    }

    default void executeObject_onFieldValuesException(Throwable t) {

    }

    default void fieldFetched(ExecutionContext executionContext,
                              Supplier<DataFetchingEnvironment> dataFetchingEnvironment,
                              ExecutionStrategyParameters executionStrategyParameters,
                              DataFetcher<?> dataFetcher,
                              CompletableFuture<Object> fetchedValue) {

    }


}
