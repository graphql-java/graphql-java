package graphql.execution;

import graphql.Internal;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Internal
public interface DataLoaderDispatchStrategy {

    String CUSTOM_STRATEGY_KEY = "CUSTOM_STRATEGY_KEY";


    DataLoaderDispatchStrategy NO_OP = new DataLoaderDispatchStrategy() {
    };


    default void executionStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {

    }

    default void executionStrategyOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {

    }

    default void executionStrategyOnFieldValuesException(Throwable t, ExecutionStrategyParameters parameters) {

    }


    default void executeObject(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {

    }

    default void executeObjectOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {

    }

    default void executeObjectOnFieldValuesException(Throwable t, ExecutionStrategyParameters parameters) {

    }

    default void fieldFetched(ExecutionContext executionContext,
                              ExecutionStrategyParameters parameters,
                              DataFetcher<?> dataFetcher,
                              Object fetchedValue) {

    }

    default void fieldFetchedDone(ExecutionContext executionContext,
                                  ExecutionStrategyParameters parameters,
                                  DataFetcher<?> dataFetcher,
                                  Object value,
                                  GraphQLObjectType parentType,
                                  GraphQLFieldDefinition fieldDefinition
    ) {

    }


    default DataFetcher<?> modifyDataFetcher(DataFetcher<?> dataFetcher) {
        return dataFetcher;
    }

    default void deferredField(ExecutionContext executionContext, MergedField currentField) {

    }
}
