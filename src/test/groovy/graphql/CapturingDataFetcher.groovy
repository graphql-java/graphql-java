package graphql

import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment

/**
 * Help to capture data fetcher arguments passed in
 */
class CapturingDataFetcher implements DataFetcher {
    Map<String, Object> args
    DataFetchingEnvironment environment

    @Override
    Object get(DataFetchingEnvironment environment) {
        this.environment = environment
        this.args = environment.getArguments()
        null
    }
}
