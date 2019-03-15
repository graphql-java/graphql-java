package graphql

import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment

import static graphql.execution.DataFetcherResult.newResult

/**
 * A data fetcher that expects a numeric local context and adds one to it.  It demonstrates using local context
 */
class ContextPassingDataFetcher implements DataFetcher {
    def skip

    ContextPassingDataFetcher() {
        this.skip = false
    }

    ContextPassingDataFetcher(boolean skip) {
        this.skip = skip
    }

    @Override
    Object get(DataFetchingEnvironment env) throws Exception {
        String data = env.getSource()

        Integer localCtx = env.getLocalContext()

        def newData = data + localCtx + ","
        def newCtx = localCtx + 1
        if (skip) {
            // when null is returned than the previous field context is passed
            // onto the next data fetcher
            newCtx = null
        }
        newResult().data(newData).localContext(newCtx).build()
    }
}
