@file:Suppress("ForbiddenImport", "TooGenericExceptionCaught")

package graphql.arbitrary

import io.kotest.property.Arb
import io.kotest.property.PropertyTesting
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.next
import java.io.PrintStream

/**
 * An Arb combined with an assertion, providing a common interface for checking all values,
 * finding the minimum violating value, and seed marching
 */
class CheckedArb<T>(
    val arb: Arb<T>,
    val check: (T) -> Unit
) {
    /** Return the minimum [Violation] within [iter] samples, if one exists */
    fun minViolation(
        comparator: Comparator<T>,
        rs: RandomSource,
        iter: Int = PropertyTesting.defaultIterationCount,
        printEvery: Int = 1_000,
        out: PrintStream = System.out
    ): Violation<T>? =
        arb.asSequence(rs)
            .take(iter)
            .foldIndexed(null as Violation<T>?) { i, acc, t ->
                if (printEvery > 0 && i.mod(printEvery) == 0) {
                    out.println("Iteration $i...")
                }
                try {
                    check(t)
                    acc
                } catch (err: Throwable) {
                    if (acc == null) {
                        out.println("Found new min violation at iteration $i")
                        Violation(t, err, rs.seed)
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        val accValue = acc.value as T
                        if (comparator.compare(t, accValue) < 0) {
                            out.println("Found new min violation at iteration $i")
                            Violation(t, err, rs.seed)
                        } else {
                            acc
                        }
                    }
                }
            }

    /** Return the first [Violation] within [maxIter] samples, if one exists */
    fun seedMarch(
        startingSeed: Long = 0,
        maxIter: Int = 100_000_000,
        printEvery: Int = 1_000,
        out: PrintStream = System.out
    ): Violation<T>? {
        var iter = 0
        while (iter < maxIter) {
            val seed = startingSeed + iter
            if (printEvery > 0 && iter.mod(printEvery) == 0) {
                out.println("Seed $seed...")
            }
            var t: T? = null
            try {
                val sample = arb.next(RandomSource.seeded(seed))
                t = sample
                check(sample)
            } catch (e: Throwable) {
                return Violation(t, e, seed)
            }
            iter += 1
        }
        return null
    }
}

/** [value] is null when generation fails before a sample is produced. */
data class Violation<T>(val value: T?, val err: Throwable, val seed: Long)

/** Return a [CheckedArb] that combines this arb with [check]. */
fun <T> Arb<T>.withCheck(check: (T) -> Unit): CheckedArb<T> = CheckedArb(this, check)
