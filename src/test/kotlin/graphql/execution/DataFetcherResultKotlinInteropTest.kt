package graphql.execution

class DataFetcherResultKotlinInteropTest {
    fun nullableNewResultFactoryCompiles(): DataFetcherResult<String?> {
        return DataFetcherResult.newResult<String?>()
            .data(null)
            .build()
    }
}
