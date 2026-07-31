package graphql.schema.property

import io.kotest.property.Arb
import io.kotest.property.PropertyTesting
import io.kotest.property.RandomSource
import io.kotest.property.Sample
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.asSample
import java.io.PrintStream

/**
 * Flatten this generator into a generator of its elements while preserving the order within
 * every generated iterable.
 *
 * The underlying generator must eventually produce a non-empty iterable.
 */
internal fun <T> Arb<Iterable<T>>.flatten(): Arb<T> = FlattenArb(map { it.iterator() })

/** Transform this generator, dropping values for which [transform] returns null. */
internal fun <T, R : Any> Arb<T>.mapNotNull(transform: (T) -> R?): Arb<R> =
    map(transform).filter { it != null }.map { requireNotNull(it) }

private class FlattenArb<T>(private val underlying: Arb<Iterator<T>>) : Arb<T>() {
    private var chunk: Iterator<T>? = null

    override fun edgecase(rs: RandomSource): Sample<T>? {
        val current = chunk
        if (current != null && current.hasNext()) return null
        val next = underlying.edgecase(rs)?.value ?: return null
        if (!next.hasNext()) return null
        chunk = next
        return next.next().asSample()
    }

    override fun sample(rs: RandomSource): Sample<T> {
        var current = chunk
        while (current == null || !current.hasNext()) {
            current = underlying.sample(rs).value
            chunk = current
        }
        return current.next().asSample()
    }
}

/** Throw a property failure that includes [seed] when supplied. */
internal fun failProperty(
    message: String,
    cause: Throwable? = null,
    seed: Long? = null
): Nothing =
    throw AssertionError(
        buildString {
            appendLine(if (seed == null) "Property failed" else "Property failed with seed $seed")
            append(message)
        },
        cause
    )

/** An arbitrary generator paired with a property that its generated values must satisfy. */
internal class CheckedArb<T>(
    val arb: Arb<T>,
    val check: (T) -> Unit
) {
    /** Return the minimum violation found in [iterations] samples, if any. */
    fun minViolation(
        comparator: Comparator<T>,
        rs: RandomSource,
        iter: Int = PropertyTesting.defaultIterationCount,
        printEvery: Int = 1_000,
        out: PrintStream = System.out
    ): Violation<T>? =
        arb.asSequence(rs)
            .take(iter)
            .foldIndexed(null as Violation<T>?) { index, minimum, value ->
                if (printEvery > 0 && index % printEvery == 0) out.println("Iteration $index...")
                checkValue(value, minimum, comparator, rs.seed, index, out)
            }

    /** March through deterministic seeds and return the first violation, if any. */
    fun seedMarch(
        startingSeed: Long = 0,
        maxIter: Int = 100_000_000,
        printEvery: Int = 1_000,
        out: PrintStream = System.out
    ): Violation<T>? {
        repeat(maxIter) { index ->
            val seed = startingSeed + index
            if (printEvery > 0 && index % printEvery == 0) out.println("Seed $seed...")
            var value: T? = null
            try {
                value = arb.next(RandomSource.seeded(seed))
                check(value)
            } catch (error: Throwable) {
                return Violation(value, error, seed)
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun checkValue(
        value: T,
        minimum: Violation<T>?,
        comparator: Comparator<T>,
        seed: Long,
        index: Int,
        out: PrintStream
    ): Violation<T>? {
        return try {
            check(value)
            minimum
        } catch (error: Throwable) {
            if (minimum == null) return Violation(value, error, seed)
            val minimumValue = minimum.value as T
            if (comparator.compare(value, minimumValue) >= 0) return minimum
            out.println("Found new min violation at iteration $index")
            Violation(value, error, seed)
        }
    }
}

/** A generated value that violated a property, with its error and source seed. */
internal data class Violation<T>(val value: T?, val err: Throwable, val seed: Long)

/** Pair this generator with [check] for failure mining and seed marching. */
internal fun <T> Arb<T>.withCheck(check: (T) -> Unit): CheckedArb<T> = CheckedArb(this, check)
