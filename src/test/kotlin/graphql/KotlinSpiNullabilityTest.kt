package graphql

import graphql.execution.DataFetcherResult
import graphql.execution.instrumentation.InstrumentationContext
import graphql.relay.Connection
import graphql.relay.ConnectionCursor
import graphql.relay.DefaultConnection
import graphql.relay.DefaultEdge
import graphql.relay.DefaultPageInfo
import graphql.relay.Edge
import graphql.schema.Coercing
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.DataFetcher
import graphql.schema.DataFetcherFactory
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import graphql.language.Value
import graphql.execution.CoercedVariables
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * End-to-end Kotlin compatibility test for JSpecify-annotated generic SPI interfaces.
 *
 * The Kotlin *compilation* of this file is the primary regression guard.  Two
 * distinct annotation problems are caught at compile time:
 *
 * 1. @Nullable on a type variable in a position Kotlin's strict JSpecify mode
 *    (`-Xjspecify-annotations=strict`) considers illegal.
 *
 * 2. Missing `T extends @Nullable Object` on a @NullMarked generic type whose
 *    members use `@Nullable T`.  Without the bound, Kotlin infers `T : Any`
 *    (non-null) and rejects nullable type arguments such as
 *    `InstrumentationContext<String?>` at the call site.
 *
 * If either problem exists the compiler rejects this file and the build fails
 * before any test runner is invoked.
 *
 * Runtime assertions then double-check that null/non-null contracts are
 * honoured the way Kotlin users would expect.
 */
class KotlinSpiNullabilityTest {

    // =========================================================================
    // InstrumentationContext<T extends @Nullable Object>
    //
    // The interface is @NullMarked with:
    //   void onCompleted(@Nullable T result, @Nullable Throwable t)
    //
    // Without `T extends @Nullable Object` Kotlin would infer T : Any and
    // reject `InstrumentationContext<String?>` at call-sites (Void, Object
    // field values, etc.).
    // =========================================================================

    /** Minimal Kotlin implementation that records the calls for assertion. */
    private class RecordingContext<T : Any> : InstrumentationContext<T> {
        var dispatched = false
        var result: T? = null
        var throwable: Throwable? = null

        override fun onDispatched() { dispatched = true }

        // @Nullable T result  →  T?  must compile under strict JSpecify mode
        override fun onCompleted(result: T?, t: Throwable?) {
            this.result = result
            this.throwable = t
        }
    }

    @Test
    fun `InstrumentationContext onCompleted accepts null for @Nullable T result`() {
        val ctx = RecordingContext<String>()
        ctx.onDispatched()
        ctx.onCompleted(null, null)
        assertTrue(ctx.dispatched)
        assertNull(ctx.result)
        assertNull(ctx.throwable)
    }

    @Test
    fun `InstrumentationContext onCompleted accepts non-null result`() {
        val ctx = RecordingContext<String>()
        ctx.onCompleted("hello", null)
        assertEquals("hello", ctx.result)
    }

    @Test
    fun `InstrumentationContext onCompleted accepts non-null throwable`() {
        val ctx = RecordingContext<String>()
        val ex = RuntimeException("boom")
        ctx.onCompleted(null, ex)
        assertSame(ex, ctx.throwable)
    }

    /**
     * Uses a nullable type argument – only compiles when the interface declares
     * `T extends @Nullable Object` (Kotlin bound: T : Any?).
     * Without the bound Kotlin infers T : Any and `String?` violates it.
     */
    @Test
    fun `InstrumentationContext accepts nullable type argument`() {
        // InstrumentationContext<Void> mirrors real engine usage (beginReactiveResults)
        // Void can only hold null, so T must allow nullable type arguments.
        val ctx = object : InstrumentationContext<Void?> {
            var completed = false
            override fun onDispatched() {}
            override fun onCompleted(result: Void?, t: Throwable?) { completed = true }
        }
        ctx.onCompleted(null, null)
        assertTrue(ctx.completed)
    }

    @Test
    fun `InstrumentationContext lambda-style implementation compiles`() {
        var seen: String? = "not-called"
        val ctx = object : InstrumentationContext<String> {
            override fun onDispatched() {}
            override fun onCompleted(result: String?, t: Throwable?) { seen = result }
        }
        ctx.onCompleted(null, null)
        assertNull(seen)
    }

    // =========================================================================
    // DataFetcherResult<T extends @Nullable Object>
    //
    // The reference implementation of the `T extends @Nullable Object` fix.
    // DataFetcherResult was corrected first; these tests document the expected
    // behaviour so regressions are caught.
    // =========================================================================

    @Test
    fun `DataFetcherResult with nullable type argument compiles and returns null data`() {
        // DataFetcher<String?> naturally produces a DataFetcherResult<String?>.
        // This requires T : Any? – i.e. T extends @Nullable Object.
        val result: DataFetcherResult<String?> = DataFetcherResult.newResult<String?>()
            .data(null)
            .build()

        // getData() is @Nullable T → String? in Kotlin
        val data: String? = result.getData()
        assertNull(data)
    }

    @Test
    fun `DataFetcherResult with non-null type argument works normally`() {
        val result: DataFetcherResult<String> = DataFetcherResult.newResult<String>()
            .data("hello")
            .build()
        assertEquals("hello", result.getData())
    }

    @Test
    fun `DataFetcherResult map transforms nullable data`() {
        // map() is declared as: <R> DataFetcherResult<R> map(Function<@Nullable T, @Nullable R>)
        // The Function type arguments are @Nullable, which must compile in strict mode.
        val original: DataFetcherResult<String?> = DataFetcherResult.newResult<String?>()
            .data(null)
            .build()

        val mapped: DataFetcherResult<Int?> = original.map { str: String? -> str?.length }
        assertNull(mapped.getData())
    }

    @Test
    fun `DataFetcherResult map transforms non-null data`() {
        val original: DataFetcherResult<String?> = DataFetcherResult.newResult<String?>()
            .data("hello")
            .build()

        val mapped: DataFetcherResult<Int?> = original.map { str: String? -> str?.length }
        assertEquals(5, mapped.getData())
    }

    @Test
    fun `DataFetcherResult builder data method accepts null`() {
        // Builder.data(@Nullable T data) – null must be a legal argument
        val builder: DataFetcherResult.Builder<String?> = DataFetcherResult.newResult()
        builder.data(null)
        assertNull(builder.build().getData())
    }

    @Test
    fun `DataFetcherResult newResult factory with nullable data compiles`() {
        // newResult(@Nullable T data) overload – verifies the factory type parameter
        // T extends @Nullable Object is in place, otherwise String? is rejected here.
        val result = DataFetcherResult.newResult<String?>(null).build()
        assertNull(result.getData())
    }

    @Test
    fun `DataFetcherResult transform preserves nullability`() {
        val original: DataFetcherResult<String?> = DataFetcherResult.newResult<String?>()
            .data(null)
            .build()

        val transformed: DataFetcherResult<String?> = original.transform { builder ->
            builder.data("replaced")
        }
        assertEquals("replaced", transformed.getData())
    }

    // =========================================================================
    // Edge<T extends @Nullable Object> / DefaultEdge<T extends @Nullable Object>
    //
    // Edge.getNode() is @Nullable T.  Without `T extends @Nullable Object` Kotlin
    // rejects Edge<String?> (and the common real-world Edge<SomeDomainObject?>).
    // =========================================================================

    @Test
    fun `DefaultEdge with non-null node compiles and returns node`() {
        val cursor = ConnectionCursor { "cursor-1" }
        val edge: Edge<String> = DefaultEdge("node-value", cursor)
        // getNode() is @Nullable T → String? in Kotlin
        val node: String? = edge.getNode()
        assertEquals("node-value", node)
    }

    @Test
    fun `DefaultEdge with nullable type argument accepts null node`() {
        // Edge<String?> requires T : Any? – only valid with T extends @Nullable Object.
        // Without the bound the compiler rejects this line.
        val cursor = ConnectionCursor { "cursor-2" }
        val edge: Edge<String?> = DefaultEdge<String?>(null, cursor)
        assertNull(edge.getNode())
    }

    @Test
    fun `Connection with nullable type argument compiles`() {
        // Connection<T> wraps Edge<T>; both need T extends @Nullable Object so that
        // Connection<String?> is a valid type.
        val cursor = ConnectionCursor { "c" }
        val edge: Edge<String?> = DefaultEdge<String?>(null, cursor)
        val pageInfo = DefaultPageInfo(null, null, false, false)
        val connection: Connection<String?> = DefaultConnection(listOf(edge), pageInfo)
        assertNull(connection.edges?.first()?.getNode())
    }

    // =========================================================================
    // DataFetcher<T> / DataFetcherFactory<T> / TrivialDataFetcher<T>
    // =========================================================================

    private class NullableStringFetcher : DataFetcher<String?> {
        override fun get(env: DataFetchingEnvironment): String? = null
    }

    private class NonNullStringFetcher : DataFetcher<String> {
        override fun get(env: DataFetchingEnvironment): String = "result"
    }

    private val lambdaFetcher: DataFetcher<Int> = DataFetcher { 42 }

    private class StringFetcherFactory : DataFetcherFactory<String> {
        @Deprecated("Deprecated in the SPI – present only to satisfy the interface contract")
        override fun get(environment: graphql.schema.DataFetcherFactoryEnvironment): DataFetcher<String> =
            NonNullStringFetcher()

        override fun get(fieldDefinition: GraphQLFieldDefinition): DataFetcher<String> =
            NonNullStringFetcher()
    }

    private class TrivialFetcher : TrivialDataFetcher<String?> {
        override fun get(env: DataFetchingEnvironment): String? = null
    }

    // =========================================================================
    // Coercing<I, O>
    //
    // Two generic parameters with mixed @Nullable/@NonNull on method signatures.
    // Compiling this class in strict mode validates every annotated position.
    // =========================================================================

    private class PassThroughCoercing : Coercing<String, String> {

        @Throws(CoercingSerializeException::class)
        override fun serialize(
            dataFetcherResult: Any,        // @NonNull Object
            graphQLContext: GraphQLContext, // @NonNull
            locale: Locale                 // @NonNull
        ): String? = dataFetcherResult.toString()   // return is @Nullable O

        @Throws(CoercingParseValueException::class)
        override fun parseValue(
            input: Any,                    // @NonNull Object
            graphQLContext: GraphQLContext,
            locale: Locale
        ): String? = input.toString()      // return is @Nullable I

        override fun parseLiteral(
            input: Value<*>,               // @NonNull Value<?>
            variables: CoercedVariables,   // @NonNull
            graphQLContext: GraphQLContext,
            locale: Locale
        ): String? = input.toString()      // return is @Nullable I
    }

    @Test
    fun `Coercing serialize returns nullable result`() {
        val coercing = PassThroughCoercing()
        val ctx = GraphQLContext.newContext().build()
        val result: String? = coercing.serialize("hello", ctx, Locale.ENGLISH)
        assertEquals("hello", result)
    }

    @Test
    fun `Coercing parseValue returns nullable result`() {
        val coercing = PassThroughCoercing()
        val ctx = GraphQLContext.newContext().build()
        val result: String? = coercing.parseValue("world", ctx, Locale.ENGLISH)
        assertEquals("world", result)
    }
}
