package graphql.execution2;

import graphql.execution2.result.ExecutionResultNode;
import graphql.schema.DataFetchingEnvironment;

public interface DelegatedExecutionDataFetcher {

    ExecutionResultNode execute(DataFetchingEnvironment environment);
}
