@file:Suppress("ForbiddenImport")

package graphql.schema.property

import java.io.File
import java.io.FileWriter
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Base class for progressively finding regressions using a [CheckedArb]
 *
 * This suite defines multiple junit test cases that represent different run
 * modes. These test cases each support a specific kind of use case for
 * verifying the correctness of [checkedArb]. See kdocs on each method for more.
 *
 * @see `check all`
 * @see `min violation`
 * @see `seed march`
 */
abstract class DeepArbSuite<T>(
    seed: Long = Random.nextLong(),
    iterations: Int = 1_000,
    private val minViolationIterations: Int = iterations * 10,
    private val seedMarchStartingSeed: Long = 0,
) : KotestPropertyBase(seed, iterations) {
    protected abstract val checkedArb: CheckedArb<T>

    protected abstract val comparator: Comparator<T>

    /**
     * This method will run for a relatively small number of iterations. It runs by
     * default in all settings, making it an economical ongoing correctness checker
     *
     * @see iterations
     */
    @Test
    fun `check all`() {
        runBlocking {
            checkedArb.arb.checkAll { checkedArb.check(it) }
        }
    }

    /**
     * Check many more samples and report the simplest failure found.
     *
     * This should be left disabled so that it doesn't slow down normal tests.
     * Implementations of this method can be run manually in Idea or via command line.
     */
    @Test
    @Disabled
    fun `min violation`() {
        checkedArb.minViolation(comparator, randomSource, minViolationIterations)
            ?.let(::dumpAndFail)
    }

    /**
     * Run forever, reporting the first failing seed.
     *
     * This should be left disabled so that it doesn't slow down normal tests.
     * Implementations of this method can be run manually in Idea or via command line.
     */
    @Test
    @Disabled
    fun `seed march`() {
        checkedArb.seedMarch(startingSeed = seedMarchStartingSeed)
            ?.let(::dumpAndFail)
    }

    private fun dumpAndFail(violation: Violation<*>) {
        val file = File.createTempFile("violation-", ".txt")
        FileWriter(file).use { writer ->
            writer.write("seed: ${violation.seed}\n")
            writer.write(violation.err.toString())
        }
        fail<Unit>("Wrote dump (seed ${violation.seed}) to:\n${file.absolutePath}")
    }
}
