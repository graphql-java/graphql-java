package graphql

import graphql.execution.instrumentation.InstrumentationContext
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
 * The Kotlin *compilation* of this file is the primary regression guard:
 * if a JSpecify annotation is placed in a position that Kotlin's strict mode
 * (`-Xjspecify-annotations=strict`) considers illegal (e.g. @Nullable on a type
 * variable declaration, or a conflicting bound in a @NullMarked context), the
 * compiler will reject this file and the build fails before any test runs.
 *
 * Runtime assertions then double-check that null/non-null contracts are
 * honoured the way Kotlin users would expect.
 */
class KotlinSpiNullabilityTest {

    // -------------------------------------------------------------------------
    // InstrumentationContext<T>
    //
    // The interface is @NullMarked.  The critical annotation under test is
    //   void onCompleted(@Nullable T result, @Nullable Throwable t)
    //
    // In strict mode Kotlin translates @Nullable T → T? (where T : Any due to
    // @NullMarked).  The combination must not produce a compiler error.
    // -------------------------------------------------------------------------

    /** Minimal Kotlin implementation that records the calls for assertion. */
    private class RecordingContext<T : Any> : InstrumentationContext<T> {
        var dispatched = false
        var result: T? = null
        var throwable: Throwable? = null

        override fun onDispatched() {
            dispatched = true
        }

        // @Nullable T result  →  T?   must compile under strict JSpecify mode
        override fun onCompleted(result: T?, t: Throwable?) {
            this.result = result
            this.throwable = t
        }
    }

    @Test
    fun `InstrumentationContext onCompleted accepts null for @Nullable T result`() {
        val ctx = RecordingContext<String>()
        ctx.onDispatched()
        // Passing null must be accepted – @Nullable T guarantees it
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
        assertNull(ctx.throwable)
    }

    @Test
    fun `InstrumentationContext onCompleted accepts non-null throwable`() {
        val ctx = RecordingContext<String>()
        val ex = RuntimeException("boom")
        ctx.onCompleted(null, ex)
        assertNull(ctx.result)
        assertSame(ex, ctx.throwable)
    }

    // -------------------------------------------------------------------------
    // DataFetcher<T>
    //
    // No JSpecify annotations yet on DataFetcher itself, but implementing it in
    // Kotlin is the baseline for future annotation additions.
    // -------------------------------------------------------------------------

    /** DataFetcher whose return value can be null. */
    private class NullableStringFetcher : DataFetcher<String?> {
        override fun get(env: DataFetchingEnvironment): String? = null
    }

    /** DataFetcher whose return value is always non-null. */
    private class NonNullStringFetcher : DataFetcher<String> {
        override fun get(env: DataFetchingEnvironment): String = "result"
    }

    /** DataFetcher expressed as a Kotlin lambda (SAM conversion). */
    private val lambdaFetcher: DataFetcher<Int> = DataFetcher { 42 }

    // -------------------------------------------------------------------------
    // DataFetcherFactory<T>
    // -------------------------------------------------------------------------

    private class StringFetcherFactory : DataFetcherFactory<String> {
        @Deprecated("Deprecated in the SPI – present only to satisfy the interface contract")
        override fun get(environment: graphql.schema.DataFetcherFactoryEnvironment): DataFetcher<String> =
            NonNullStringFetcher()

        override fun get(fieldDefinition: GraphQLFieldDefinition): DataFetcher<String> =
            NonNullStringFetcher()
    }

    // -------------------------------------------------------------------------
    // TrivialDataFetcher<T>  (extends DataFetcher<T>)
    // -------------------------------------------------------------------------

    private class TrivialFetcher : TrivialDataFetcher<String?> {
        override fun get(env: DataFetchingEnvironment): String? = null
    }

    // -------------------------------------------------------------------------
    // Coercing<I, O>
    //
    // Two generic parameters with mixed @Nullable/@NonNull on method signatures.
    // Compiling this class in strict mode validates every annotated position.
    // -------------------------------------------------------------------------

    /**
     * Minimal String-to-String Coercing.  Overrides only the current
     * (non-deprecated) method variants.
     *
     * Return type of serialize is @Nullable O → String?  in Kotlin.
     * Return type of parseValue is @Nullable I → String?  in Kotlin.
     */
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
        // @Nullable O return – result may be null, so Kotlin type is String?
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

    // -------------------------------------------------------------------------
    // Using InstrumentationContext<T> with a concrete bounded type
    //
    // Verifies that using the interface from Kotlin call-sites also compiles
    // correctly – not just implementing it.
    // -------------------------------------------------------------------------

    @Test
    fun `InstrumentationContext call-site usage with concrete type parameter`() {
        // Type argument is a Kotlin nullable type – exercises T = String?
        val ctx = RecordingContext<String>()

        // Calling through the interface type confirms Kotlin is happy with the
        // @Nullable T parameter on the declared interface type.
        val iface: InstrumentationContext<String> = ctx
        iface.onCompleted(null, null)

        assertNull(ctx.result)
    }

    @Test
    fun `InstrumentationContext lambda-style implementation compiles`() {
        // Anonymous object implementing the interface in the most idiomatic
        // Kotlin style – ensuring no annotation-driven compile error.
        var seen: String? = "not-called"
        val ctx = object : InstrumentationContext<String> {
            override fun onDispatched() {}
            override fun onCompleted(result: String?, t: Throwable?) {
                seen = result
            }
        }
        ctx.onCompleted(null, null)
        assertNull(seen)
    }
}
