package graphql.execution

/**
 * Verifies at compile time that Kotlin callers can use nullable type arguments with
 * [DataFetcherResult.newResult].
 *
 * This class intentionally has no runtime test annotation. The assertion is that
 * `compileTestKotlin` successfully type-checks `String?` through the builder chain.
 */
class DataFetcherResultKotlinInteropTest {
    fun nullableNewResultFactoryCompiles(): DataFetcherResult<String?> {
        return DataFetcherResult.newResult<String?>()
            .data(null)
            .build()
    }
}
