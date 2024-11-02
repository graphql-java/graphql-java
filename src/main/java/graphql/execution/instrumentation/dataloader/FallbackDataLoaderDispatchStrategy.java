package graphql.execution.instrumentation.dataloader;

import graphql.Internal;
import graphql.execution.DataLoaderDispatchStrategy;
import graphql.execution.ExecutionContext;
import graphql.schema.DataFetcher;


/**
 * Used when we cant guarantee the fields will be counted right: simply dispatch always after each DF.
 */
@Internal
public class FallbackDataLoaderDispatchStrategy implements DataLoaderDispatchStrategy {

    private final ExecutionContext executionContext;

    public FallbackDataLoaderDispatchStrategy(ExecutionContext executionContext) {
        this.executionContext = executionContext;
    }


    @Override
    public DataFetcher<?> modifyDataFetcher(DataFetcher<?> dataFetcher) {
        return (DataFetcher<Object>) environment -> {
            Object obj = dataFetcher.get(environment);
            executionContext.getDataLoaderRegistry().dispatchAll();
            return obj;
        };

    }
}
