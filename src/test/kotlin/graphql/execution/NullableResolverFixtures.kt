package graphql.execution

import graphql.schema.AsyncDataFetcher
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import java.util.concurrent.CompletableFuture

// Compiled by the existing Kotlin test task to verify the published SPI contracts.
class NullableResolverFixtures {
    fun nullableResolver(): DataFetcher<String> = DataFetcher { null }

    fun nullableAsyncResult(environment: DataFetchingEnvironment): CompletableFuture<String?> =
        AsyncDataFetcher.async(nullableResolver()).get(environment)
}
